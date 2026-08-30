package com.javelinco.localmusicplayer.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recent_plays` (
                    `kind` TEXT NOT NULL,
                    `itemId` TEXT NOT NULL,
                    `playedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`kind`, `itemId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recent_plays_playedAtEpochMs` " +
                    "ON `recent_plays` (`playedAtEpochMs`)",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ignored_tracks` (
                    `ignoreId` TEXT NOT NULL,
                    `trackId` TEXT,
                    `sourceId` TEXT,
                    `contentUri` TEXT,
                    `relativePath` TEXT,
                    `fileName` TEXT NOT NULL,
                    `title` TEXT,
                    `artist` TEXT,
                    `normalizedTitle` TEXT NOT NULL,
                    `normalizedArtist` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `ignoredAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`ignoreId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ignored_tracks_sourceId` " +
                    "ON `ignored_tracks` (`sourceId`)",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tracks` ADD COLUMN `parentDocumentId` TEXT")
        }
    }
}
