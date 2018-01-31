package net.frju.flym.data.dao

import android.arch.lifecycle.LiveData
import android.arch.paging.DataSource
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update
import net.frju.flym.data.entities.Entry
import net.frju.flym.data.entities.EntryWithFeed

private const val ORDER_BY = "ORDER BY publicationDate DESC, id"
private const val JOIN = "entries INNER JOIN feeds ON entries.feedId = feeds.feedId"
private const val OLDER = "fetchDate <= :maxDate"
private const val FEED_ID = "entries.feedId IS :feedId"
private const val LIKE_SEARCH = "LIKE '%' || :searchText || '%'"

@Dao
interface EntryDao {

	@Query("SELECT * FROM $JOIN WHERE title $LIKE_SEARCH OR description $LIKE_SEARCH OR mobilizedContent $LIKE_SEARCH $ORDER_BY")
	fun observeSearch(searchText: String): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT * FROM $JOIN WHERE $OLDER $ORDER_BY")
	fun observeAll(maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT * FROM $JOIN WHERE $OLDER AND read = 0 $ORDER_BY")
	fun observeAllUnreads(maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT * FROM $JOIN WHERE $OLDER AND favorite = 1 $ORDER_BY")
	fun observeAllFavorites(maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT id FROM entries WHERE $OLDER $ORDER_BY")
	fun observeAllIds(maxDate: Long): LiveData<List<String>>

	@Query("SELECT id FROM entries WHERE $OLDER AND read = 0 $ORDER_BY")
	fun observeAllUnreadIds(maxDate: Long): LiveData<List<String>>

	@Query("SELECT id FROM entries WHERE $OLDER AND favorite = 1 $ORDER_BY")
	fun observeAllFavoriteIds(maxDate: Long): LiveData<List<String>>

	@Query("SELECT * FROM $JOIN WHERE $FEED_ID AND $OLDER $ORDER_BY")
	fun observeByFeed(feedId: Long, maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT * FROM $JOIN WHERE $FEED_ID AND $OLDER AND read = 0 $ORDER_BY")
	fun observeUnreadsByFeed(feedId: Long, maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT * FROM $JOIN WHERE $FEED_ID AND $OLDER AND favorite = 1 $ORDER_BY")
	fun observeFavoritesByFeed(feedId: Long, maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT id FROM entries WHERE title $LIKE_SEARCH OR description $LIKE_SEARCH OR mobilizedContent $LIKE_SEARCH $ORDER_BY")
	fun observeIdsBySearch(searchText: String): LiveData<List<String>>

	@Query("SELECT id FROM entries WHERE feedId IS :feedId AND $OLDER $ORDER_BY")
	fun observeIdsByFeed(feedId: Long, maxDate: Long): LiveData<List<String>>

	@Query("SELECT id FROM entries WHERE feedId IS :feedId AND $OLDER AND read = 0 $ORDER_BY")
	fun observeUnreadIdsByFeed(feedId: Long, maxDate: Long): LiveData<List<String>>

	@Query("SELECT id FROM entries WHERE feedId IS :feedId AND $OLDER AND favorite = 1 $ORDER_BY")
	fun observeFavoriteIdsByFeed(feedId: Long, maxDate: Long): LiveData<List<String>>

	@Query("SELECT * FROM $JOIN WHERE groupId IS :groupId AND $OLDER $ORDER_BY")
	fun observeByGroup(groupId: Long, maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT * FROM $JOIN WHERE groupId IS :groupId AND $OLDER AND read = 0 $ORDER_BY")
	fun observeUnreadsByGroup(groupId: Long, maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT * FROM $JOIN WHERE groupId IS :groupId AND $OLDER AND favorite = 1 $ORDER_BY")
	fun observeFavoritesByGroup(groupId: Long, maxDate: Long): DataSource.Factory<Int, EntryWithFeed>

	@Query("SELECT id FROM $JOIN WHERE groupId IS :groupId AND $OLDER $ORDER_BY")
	fun observeIdsByGroup(groupId: Long, maxDate: Long): LiveData<List<String>>

	@Query("SELECT id FROM $JOIN WHERE groupId IS :groupId AND $OLDER AND read = 0 $ORDER_BY")
	fun observeUnreadIdsByGroup(groupId: Long, maxDate: Long): LiveData<List<String>>

	@Query("SELECT id FROM $JOIN WHERE groupId IS :groupId AND $OLDER AND favorite = 1 $ORDER_BY")
	fun observeFavoriteIdsByGroup(groupId: Long, maxDate: Long): LiveData<List<String>>

	@Query("SELECT COUNT(*) FROM entries WHERE read = 0 AND fetchDate > :minDate")
	fun observeNewEntriesCount(minDate: Long): LiveData<Long>

	@Query("SELECT COUNT(*) FROM entries WHERE $FEED_ID AND read = 0 AND fetchDate > :minDate")
	fun observeNewEntriesCountByFeed(feedId: Long, minDate: Long): LiveData<Long>

	@Query("SELECT COUNT(*) FROM $JOIN WHERE groupId IS :groupId AND read = 0 AND fetchDate > :minDate")
	fun observeNewEntriesCountByGroup(groupId: Long, minDate: Long): LiveData<Long>

	@get:Query("SELECT * FROM $JOIN WHERE favorite = 1")
	val favorites: List<EntryWithFeed>

	@get:Query("SELECT COUNT(*) FROM entries WHERE read = 0")
	val countUnread: Long

	@Query("SELECT * FROM entries WHERE id IS :id LIMIT 1")
	fun findById(id: String): Entry?

	@Query("SELECT * FROM $JOIN WHERE id IS :id LIMIT 1")
	fun findByIdWithFeed(id: String): EntryWithFeed?

	@Query("SELECT id FROM entries WHERE feedId IS (:feedId)")
	fun idsForFeed(feedId: Long): List<String>

	@Query("UPDATE entries SET read = 1 WHERE id IN (:ids)")
	fun markAsRead(ids: List<String>)

	@Query("UPDATE entries SET read = 0 WHERE id IN (:ids)")
	fun markAsUnread(ids: List<String>)

	@Query("UPDATE entries SET read = 1 WHERE feedId = :feedId")
	fun markAsRead(feedId: Long)

	@Query("DELETE FROM entries WHERE fetchDate < :keepDateBorderTime AND favorite = 0 AND read = 0")
	fun deleteOlderThan(keepDateBorderTime: Long)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insert(vararg entries: Entry)

	@Update
	fun update(vararg entries: Entry)

	@Delete
	fun delete(vararg entries: Entry)
}