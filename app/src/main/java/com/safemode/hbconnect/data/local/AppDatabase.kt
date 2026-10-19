package com.safemode.hbconnect.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's local SQLite database (Room). It backs the offline write queue and a local copy of the
 * inventory for offline reads. exportSchema is off; schema changes ship with an explicit [Migration]
 * (see [build]) because the queue holds the user's unsent changes and must survive an upgrade.
 */
@Database(
    entities = [QueuedOpEntity::class, CachedEntity::class, CachedDetail::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedOps(): QueuedOpDao
    abstract fun cachedEntities(): CachedEntityDao
    abstract fun cachedDetails(): CachedDetailDao

    companion object {
        /**
         * 2 -> 3: scopes queued writes and cached rows to a saved server profile. Existing queued
         * writes are kept (serverId stays null, so they replay against the active server as before).
         * Cached inventory rows can't be attributed to a server, so they are dropped; the background
         * sync refills them.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queued_op ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE cached_entity ADD COLUMN serverId TEXT NOT NULL DEFAULT ''")
                db.execSQL("DELETE FROM cached_entity")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hbconnect.db")
                .addMigrations(MIGRATION_2_3)
                // No blanket destructive fallback: a version bump without a migration fails loudly in
                // testing instead of silently wiping the user's queued writes. Version 1 predates
                // migrations and is rebuilt, as is a downgrade (e.g. reinstalling an older build).
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
