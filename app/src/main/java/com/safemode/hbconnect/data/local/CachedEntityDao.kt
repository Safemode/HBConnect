package com.safemode.hbconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Reads and writes the locally cached inventory ([CachedEntity]). Queries return the stored
 * [CachedEntity.summaryJson] strings; the repository decodes them back into entities. Every read is
 * scoped to one server and collection. Name matching is case-insensitive (COLLATE NOCASE) to mirror
 * the server's ordering and search.
 */
@Dao
interface CachedEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CachedEntity>)

    /** Items (isLocation=false) or locations (isLocation=true) for a collection, name-sorted. */
    @Query(
        "SELECT summaryJson FROM cached_entity " +
            "WHERE serverId = :server AND tenant = :tenant AND isLocation = :isLocation " +
            "AND (:includeArchived OR archived = 0) " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    suspend fun list(server: String, tenant: String, isLocation: Boolean, includeArchived: Boolean): List<String>

    /** The entities contained directly within [parentId] (a location's items), name-sorted. */
    @Query(
        "SELECT summaryJson FROM cached_entity " +
            "WHERE serverId = :server AND tenant = :tenant AND parentId = :parentId " +
            "AND (:includeArchived OR archived = 0) " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    suspend fun childrenOf(server: String, tenant: String, parentId: String, includeArchived: Boolean): List<String>

    /** Name search across both items and locations for a collection. */
    @Query(
        "SELECT summaryJson FROM cached_entity " +
            "WHERE serverId = :server AND tenant = :tenant AND (:includeArchived OR archived = 0) " +
            "AND name LIKE '%' || :query || '%' " +
            "ORDER BY name COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun search(server: String, tenant: String, query: String, includeArchived: Boolean, limit: Int): List<String>

    /** Every cached id for a server and collection (used to prune entities deleted on the server). */
    @Query("SELECT id FROM cached_entity WHERE serverId = :server AND tenant = :tenant")
    suspend fun idsIn(server: String, tenant: String): List<String>

    @Query("DELETE FROM cached_entity WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    /** Drops everything cached for one server (e.g. when that saved server is deleted). */
    @Query("DELETE FROM cached_entity WHERE serverId = :server")
    suspend fun clearServer(server: String)

    @Query("DELETE FROM cached_entity")
    suspend fun clearAll()
}
