package com.linedraw.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawDao {
    @Query("SELECT * FROM draws ORDER BY ordinal") fun watchDraws(): Flow<List<Draw>>
    @Query("SELECT * FROM records ORDER BY updatedAt DESC") fun watchRecords(): Flow<List<Record>>
    @Query("SELECT * FROM batches ORDER BY createdAt DESC LIMIT 1") fun watchBatch(): Flow<Batch?>
    @Query("SELECT * FROM batch_items ORDER BY position") fun watchItems(): Flow<List<BatchItem>>
    @Query("SELECT * FROM metadata") fun watchMetadata(): Flow<List<Metadata>>
    @Query("SELECT * FROM metadata WHERE `key` = :key") suspend fun meta(key: String): Metadata?
    @Upsert suspend fun meta(value: Metadata)
    @Query("DELETE FROM metadata WHERE `key` = :key") suspend fun deleteMeta(key: String)
    @Query("SELECT * FROM draws WHERE rowKey IN (:keys) ORDER BY ordinal") suspend fun draws(keys: List<String>): List<Draw>
    @Query("SELECT * FROM draws WHERE demo = :demo AND archived = 0 ORDER BY ordinal") suspend fun currentDraws(demo: Boolean): List<Draw>
    @Query("UPDATE draws SET archived = 1 WHERE demo = :demo") suspend fun archive(demo: Boolean)
    @Query("UPDATE draws SET archived = 1 WHERE demo = 0 AND rowKey NOT IN (:testKeys)") suspend fun archiveWebsite(testKeys: List<String>)
    @Query("UPDATE draws SET archived = 1 WHERE rowKey IN (:keys)") suspend fun archiveRows(keys: List<String>)
    @Upsert suspend fun upsertDraws(draws: List<Draw>)
    @Query("UPDATE draws SET activityKey = :canonical WHERE activityKey = :oldKey") suspend fun resolveIdentity(oldKey: String, canonical: String)
    @Query("UPDATE draws SET activityKey = :canonical WHERE url = :url") suspend fun resolveUrl(url: String, canonical: String)
    @Query("SELECT COUNT(*) FROM batch_items i JOIN batches b ON i.batchId = b.id WHERE b.profile = :profile AND i.activityKey = :key AND i.friendAttempted = 1") suspend fun friendWasAttempted(profile: String, key: String): Int
    @Query("SELECT i.* FROM batch_items i JOIN batches b ON i.batchId = b.id WHERE b.profile = :profile AND i.activityKey = :key AND i.submitted = 1 ORDER BY i.updatedAt DESC LIMIT 1") suspend fun lastSubmittedItem(profile: String, key: String): BatchItem?
    @Query("SELECT * FROM records WHERE profile = :profile AND activityKey = :key") suspend fun record(profile: String, key: String): Record?
    @Upsert suspend fun saveRecord(record: Record)
    @Query("DELETE FROM records WHERE profile = :profile AND activityKey = :key AND status = 'MANUAL'") suspend fun undoManual(profile: String, key: String)
    @Query("SELECT * FROM batches WHERE state IN ('RUNNING','PAUSED') ORDER BY createdAt DESC LIMIT 1") suspend fun activeBatch(): Batch?
    @Query("SELECT * FROM batches ORDER BY createdAt DESC LIMIT 1") suspend fun latestBatch(): Batch?
    @Upsert suspend fun saveBatch(batch: Batch)
    @Upsert suspend fun saveItems(items: List<BatchItem>)
    @Upsert suspend fun saveItem(item: BatchItem)
    @Query("SELECT * FROM batch_items WHERE batchId = :batchId ORDER BY position") suspend fun items(batchId: String): List<BatchItem>
    @Query("SELECT * FROM batch_items WHERE batchId = :batchId AND position = :position") suspend fun item(batchId: String, position: Int): BatchItem?
    @Insert suspend fun addAttempt(attempt: Attempt): Long
    @Query("SELECT * FROM attempts WHERE id = :id") suspend fun attempt(id: Long): Attempt?
    @Query("UPDATE attempts SET disposition = :disposition WHERE id = :id") suspend fun attemptResult(id: Long, disposition: String)
    @Query("SELECT * FROM attempts ORDER BY at DESC LIMIT 300") suspend fun attempts(): List<Attempt>
    @Query("DELETE FROM attempts WHERE at < :before") suspend fun pruneAttempts(before: Long)
    @Query("DELETE FROM attempts") suspend fun clearAttempts()
}

@Database(entities = [Draw::class, Record::class, Batch::class, BatchItem::class, Attempt::class, Metadata::class], version = 1, exportSchema = true)
abstract class DrawDatabase : RoomDatabase() { abstract fun dao(): DrawDao }
