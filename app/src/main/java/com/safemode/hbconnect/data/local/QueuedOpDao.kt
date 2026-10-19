package com.safemode.hbconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Reads and writes the offline outbox ([QueuedOpEntity]). FIFO order is createdAt then id. */
@Dao
interface QueuedOpDao {
    @Query("SELECT * FROM queued_op ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<QueuedOpEntity>>

    @Query("SELECT * FROM queued_op ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<QueuedOpEntity>

    @Query("SELECT COUNT(*) FROM queued_op")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(op: QueuedOpEntity)

    /** Writes several rows in one transaction (all or nothing). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ops: List<QueuedOpEntity>)

    @Query("SELECT * FROM queued_op WHERE serverId = :serverId")
    suspend fun forServer(serverId: String): List<QueuedOpEntity>

    @Query("DELETE FROM queued_op WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM queued_op")
    suspend fun clear()
}
