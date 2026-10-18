package com.safemode.hbconnect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A locally cached entity (item or location) used for offline reads and offline search. A few
 * columns are indexed for filtering and sorting; [summaryJson] holds the full
 * [com.safemode.hbconnect.data.remote.models.EntitySummary] so a row reconstructs faithfully.
 *
 * Rows are upserted whenever a server fetch succeeds and read back when the server is unreachable,
 * so the lists and search keep working offline from the last-synced copy. [tenant] scopes a row to
 * the collection it came from (blank means the server's default group).
 */
@Entity(tableName = "cached_entity")
data class CachedEntity(
    @PrimaryKey val id: String,
    val tenant: String,
    val isLocation: Boolean,
    val name: String,
    val archived: Boolean,
    val updatedAt: String,
    val summaryJson: String,
)
