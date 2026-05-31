package com.example.voicerecorderlocation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecordingSessionEntity::class,
        LocationPointEntity::class,
        PlaceMarkerEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun placeMarkerDao(): PlaceMarkerDao

    companion object {
        // v1 → v2: add waveform column + place_markers table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN waveform TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS place_markers (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        sessionId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        elapsedMillis INTEGER NOT NULL,
                        recordedAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES recording_sessions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_place_markers_sessionId ON place_markers(sessionId)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "recordings.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
