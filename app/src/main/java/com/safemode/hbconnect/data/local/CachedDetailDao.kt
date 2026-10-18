package com.safemode.hbconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Reads and writes cached full-entity detail ([CachedDetail]) for offline detail screens. */
@Dao
interface CachedDetailDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detail: CachedDetail)

    /** The stored detail JSON for an entity, or null if it isn't cached. */
    @Query("SELECT detailJson FROM cached_detail WHERE id = :id")
    suspend fun detail(id: String): String?

    /** The updatedAt of the cached detail, so the sync can skip entities already current. */
    @Query("SELECT updatedAt FROM cached_detail WHERE id = :id")
    suspend fun stamp(id: String): String?

    @Query("DELETE FROM cached_detail")
    suspend fun clearAll()
}
