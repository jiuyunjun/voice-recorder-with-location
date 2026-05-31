package com.example.voicerecorderlocation.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val audioPath: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val durationMillis: Long = 0,
    /** Downsampled amplitude CSV (0..100), ~160 buckets. Null for recordings made before v2. */
    val waveform: String? = null
)
