package net.frju.flym.ui.entries

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_entries.*
import kotlinx.android.synthetic.main.view_entry.view.*
import kotlinx.android.synthetic.main.view_main_containers.*
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.EntryWithFeed
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.utils.PrefUtils
import net.frju.flym.service.FetcherService
import net.frju.flym.ui.about.AboutActivity
import net.frju.flym.ui.main.MainNavigator
import org.jetbrains.anko.bundleOf
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.notificationManager
import org.jetbrains.anko.sdk21.listeners.onClick
import org.jetbrains.anko.support.v4.startActivity
import q.rorbin.badgeview.Badge
import q.rorbin.badgeview.QBadgeView
import java.util.*


class EntriesFragment : Fragment() {

	companion object {

		private val ARG_FEED = "ARG_FEED"
		private val STATE_FEED = "STATE_FEED"
		private val STATE_SEARCH_TEXT = "STATE_SEARCH_TEXT"
		private val STATE_SELECTED_ENTRY_ID = "STATE_SELECTED_ENTRY_ID"
		private val STATE_LIST_DISPLAY_DATE = "STATE_LIST_DISPLAY_DATE"

		fun newInstance(feed: Feed?): EntriesFragment {
			val fragment = EntriesFragment()
			feed?.let {
				fragment.arguments = bundleOf(ARG_FEED to feed)
			}
			return fragment
		}
	}

	var feed: Feed? = null
		set(value) {
			field = value

			setupToolbar()
			bottom_navigation.post { initDataObservers() } // Needed to retrieve the correct selected tab position
		}

	private val navigator: MainNavigator by lazy { activity as MainNavigator }

	private val adapter = EntryAdapter({ entry ->
		navigator.goToEntryDetails(entry, entryIds!!)
	}, { entry ->
		entry.favorite = !entry.favorite

		view?.favorite_icon?.let {
			if (entry.favorite) {
				it.setImageResource(R.drawable.ic_star_white_24dp)
			} else {
				it.setImageResource(R.drawable.ic_star_border_white_24dp)
			}
		}

		doAsync {
			App.db.entryDao().insert(entry)
		}
	})
	private var listDisplayDate = Date().time
	private var entriesLiveData: LiveData<PagedList<EntryWithFeed>>? = null
	private var entryIdsLiveData: LiveData<List<String>>? = null
	private var entryIds: List<String>? = null
	private var newCountLiveData: LiveData<Long>? = null
	private var unreadBadge: Badge? = null
	private var searchText: String? = null
	private val searchHandler = Handler()

	private val prefListener = OnSharedPreferenceChangeListener { sharedPreferences, key ->
		if (PrefUtils.IS_REFRESHING == key) {
			refreshSwipeProgress()
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
			inflater.inflate(R.layout.fragment_entries, container, false)

	override fun onActivityCreated(savedInstanceState: Bundle?) {
		super.onActivityCreated(savedInstanceState)

		if (savedInstanceState != null) {
			feed = savedInstanceState.getParcelable(STATE_FEED)
			adapter.selectedEntryId = savedInstanceState.getString(STATE_SELECTED_ENTRY_ID)
			listDisplayDate = savedInstanceState.getLong(STATE_LIST_DISPLAY_DATE)
			searchText = savedInstanceState.getString(STATE_SEARCH_TEXT)
		} else {
			feed = arguments?.getParcelable(ARG_FEED)
		}

		setupRecyclerView()

		bottom_navigation.setOnNavigationItemSelectedListener {
			recycler_view.post {
				listDisplayDate = Date().time
				initDataObservers()
				recycler_view.scrollToPosition(0)
			}
			true
		}

		unreadBadge = QBadgeView(context).bindTarget((bottom_navigation.getChildAt(0) as ViewGroup).getChildAt(0)).apply {
			setGravityOffset(35F, 0F, true)
			isShowShadow = false
			badgeBackgroundColor = ContextCompat.getColor(context!!, R.color.colorPrimaryDark)
		}

		read_all_fab.onClick {
			entryIds?.let { entryIds ->
				if (entryIds.isNotEmpty()) {
					doAsync {
						// TODO check if limit still needed
						entryIds.withIndex().groupBy { it.index / 300 }.map { it.value.map { it.value } }.forEach {
							App.db.entryDao().markAsRead(it)
						}
					}

					longSnackbar(coordinator, "Marked as read", "Undo") {
						doAsync {
							// TODO check if limit still needed
							entryIds.withIndex().groupBy { it.index / 300 }.map { it.value.map { it.value } }.forEach {
								App.db.entryDao().markAsUnread(it)
							}
						}
					}
				}

				listDisplayDate = Date().time
				initDataObservers()

				if (feed == null || feed?.id == Feed.ALL_ENTRIES_ID) {
					activity?.notificationManager?.cancel(0)
				}
			}
		}
	}

	private fun initDataObservers() {
		entryIdsLiveData?.removeObservers(this)
		entryIdsLiveData = when {
			searchText != null -> App.db.entryDao().observeIdsBySearch(searchText!!)
			feed?.isGroup == true && bottom_navigation.selectedItemId == R.id.unreads -> App.db.entryDao().observeUnreadIdsByGroup(feed!!.id, listDisplayDate)
			feed?.isGroup == true && bottom_navigation.selectedItemId == R.id.favorites -> App.db.entryDao().observeFavoriteIdsByGroup(feed!!.id, listDisplayDate)
			feed?.isGroup == true -> App.db.entryDao().observeIdsByGroup(feed!!.id, listDisplayDate)

			feed != null && feed?.id != Feed.ALL_ENTRIES_ID && bottom_navigation.selectedItemId == R.id.unreads -> App.db.entryDao().observeUnreadIdsByFeed(feed!!.id, listDisplayDate)
			feed != null && feed?.id != Feed.ALL_ENTRIES_ID && bottom_navigation.selectedItemId == R.id.favorites -> App.db.entryDao().observeFavoriteIdsByFeed(feed!!.id, listDisplayDate)
			feed != null && feed?.id != Feed.ALL_ENTRIES_ID -> App.db.entryDao().observeIdsByFeed(feed!!.id, listDisplayDate)

			bottom_navigation.selectedItemId == R.id.unreads -> App.db.entryDao().observeAllUnreadIds(listDisplayDate)
			bottom_navigation.selectedItemId == R.id.favorites -> App.db.entryDao().observeAllFavoriteIds(listDisplayDate)
			else -> App.db.entryDao().observeAllIds(listDisplayDate)
		}

		entryIdsLiveData?.observe(this, Observer<List<String>> { list ->
			entryIds = list
		})

		entriesLiveData?.removeObservers(this)
		entriesLiveData = LivePagedListBuilder(when {
			searchText != null -> App.db.entryDao().observeSearch(searchText!!)
			feed?.isGroup == true && bottom_navigation.selectedItemId == R.id.unreads -> App.db.entryDao().observeUnreadsByGroup(feed!!.id, listDisplayDate)
			feed?.isGroup == true && bottom_navigation.selectedItemId == R.id.favorites -> App.db.entryDao().observeFavoritesByGroup(feed!!.id, listDisplayDate)
			feed?.isGroup == true -> App.db.entryDao().observeByGroup(feed!!.id, listDisplayDate)

			feed != null && feed?.id != Feed.ALL_ENTRIES_ID && bottom_navigation.selectedItemId == R.id.unreads -> App.db.entryDao().observeUnreadsByFeed(feed!!.id, listDisplayDate)
			feed != null && feed?.id != Feed.ALL_ENTRIES_ID && bottom_navigation.selectedItemId == R.id.favorites -> App.db.entryDao().observeFavoritesByFeed(feed!!.id, listDisplayDate)
			feed != null && feed?.id != Feed.ALL_ENTRIES_ID -> App.db.entryDao().observeByFeed(feed!!.id, listDisplayDate)

			bottom_navigation.selectedItemId == R.id.unreads -> App.db.entryDao().observeAllUnreads(listDisplayDate)
			bottom_navigation.selectedItemId == R.id.favorites -> App.db.entryDao().observeAllFavorites(listDisplayDate)
			else -> App.db.entryDao().observeAll(listDisplayDate)
		}, 30).build()

		entriesLiveData?.observe(this, Observer<PagedList<EntryWithFeed>> { pagedList ->
			adapter.submitList(pagedList)
		})

		newCountLiveData?.removeObservers(this)
		newCountLiveData = when {
			feed?.isGroup == true -> App.db.entryDao().observeNewEntriesCountByGroup(feed!!.id, listDisplayDate)
			feed != null && feed?.id != Feed.ALL_ENTRIES_ID -> App.db.entryDao().observeNewEntriesCountByFeed(feed!!.id, listDisplayDate)
			else -> App.db.entryDao().observeNewEntriesCount(listDisplayDate)
		}

		newCountLiveData?.observe(this, Observer<Long> { count ->
			if (count != null && count > 0L) {
				// If we have an empty list, let's immediately display the new items
				if (entryIds?.isEmpty() == true && bottom_navigation.selectedItemId != R.id.favorites) {
					listDisplayDate = Date().time
					initDataObservers()
				} else {
					unreadBadge?.badgeNumber = count.toInt()
				}
			} else {
				unreadBadge?.hide(false)
			}
		})
	}

	override fun onStart() {
		super.onStart()
		PrefUtils.registerOnPrefChangeListener(prefListener)
		refreshSwipeProgress()
	}

	override fun onStop() {
		super.onStop()
		PrefUtils.unregisterOnPrefChangeListener(prefListener)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putParcelable(STATE_FEED, feed)
		outState.putString(STATE_SELECTED_ENTRY_ID, adapter.selectedEntryId)
		outState.putLong(STATE_LIST_DISPLAY_DATE, listDisplayDate)
		outState.putString(STATE_SEARCH_TEXT, searchText)

		super.onSaveInstanceState(outState)
	}

	private fun setupRecyclerView() {
		recycler_view.setHasFixedSize(true)

		val layoutManager = LinearLayoutManager(activity)
		recycler_view.layoutManager = layoutManager
		recycler_view.adapter = adapter

		refresh_layout.setColorScheme(R.color.colorAccent,
				R.color.colorPrimaryDark,
				R.color.colorAccent,
				R.color.colorPrimaryDark)

		refresh_layout.setOnRefreshListener {
			startRefresh()
		}

		recycler_view.emptyView = empty_view
	}

	private fun startRefresh() {
		if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
			if (feed?.isGroup == false && feed?.id != Feed.ALL_ENTRIES_ID) {
				context?.startService(Intent(context, FetcherService::class.java).setAction(FetcherService.ACTION_REFRESH_FEEDS).putExtra(FetcherService.EXTRA_FEED_ID,
						feed?.id))
			} else {
				context?.startService(Intent(context, FetcherService::class.java).setAction(FetcherService.ACTION_REFRESH_FEEDS))
			}
		}

		// In case there is no internet, the service won't even start, let's quickly stop the refresh animation
		refresh_layout.postDelayed({ refreshSwipeProgress() }, 500)
	}

	private fun setupToolbar() {
		activity?.toolbar?.apply {
			if (feed == null || feed?.id == Feed.ALL_ENTRIES_ID) {
				setTitle(R.string.all_entries)
			} else {
				title = feed?.title
			}

			menu.clear()
			inflateMenu(R.menu.fragment_entries)

			val searchItem = menu.findItem(R.id.menu_entries__search)
			val searchView = searchItem.actionView as SearchView
			if (searchText != null) {
				searchItem.expandActionView()
				searchView.post( // Without that, it just does not work
						{
							searchView.setQuery(searchText, false)
							searchView.clearFocus()
						})
			}

			searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
				override fun onQueryTextSubmit(query: String): Boolean {
					return false
				}

				override fun onQueryTextChange(newText: String): Boolean {
					if (searchText != null) { // needed because it can actually be called after the onMenuItemActionCollapse event
						searchText = newText

						// In order to avoid plenty of request, we add a small throttle time
						searchHandler.removeCallbacksAndMessages(null)
						searchHandler.postDelayed({
							initDataObservers()
						}, 700)
					}
					return false
				}
			})
			searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
				override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
					searchText = ""
					initDataObservers()
					return true
				}

				override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
					searchText = null
					initDataObservers()
					return true
				}
			})

			setOnMenuItemClickListener { item ->
				when (item.itemId) {
					R.id.menu_entries__about -> {
						startActivity<AboutActivity>()
						true
					}
					else -> false
				}
			}
		}
	}

	fun setSelectedEntryId(selectedEntryId: String) {
		adapter.selectedEntryId = selectedEntryId
	}

	private fun refreshSwipeProgress() {
		refresh_layout.isRefreshing = PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)
	}
}
