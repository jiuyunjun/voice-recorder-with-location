package com.example.voicerecorderlocation.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("recordedAtMillis")]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val recordedAtMillis: Long,
    val elapsedRealtimeNanos: Long
)

