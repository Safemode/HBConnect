package com.safemode.hbconnect.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's local SQLite database (Room). It backs the offline write queue and - as offline-first
 * reads are added - a local copy of the inventory. exportSchema is off because the app recreates the
 * DB on a version bump (see [build]) rather than shipping migrations: nothing here is a source of
 * truth that must survive a schema change, since the server is authoritative and the queue is small.
 */
@Database(
    entities = [QueuedOpEntity::class, CachedEntity::class, CachedDetail::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedOps(): QueuedOpDao
    abstract fun cachedEntities(): CachedEntityDao
    abstract fun cachedDetails(): CachedDetailDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hbconnect.db")
                // The DB is a cache/outbox rebuilt from the server, so drop and recreate on a schema
                // change instead of writing migrations. Queued writes are flushed on connect, so the
                // window where a pending row could be lost to an upgrade is small and acceptable.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
