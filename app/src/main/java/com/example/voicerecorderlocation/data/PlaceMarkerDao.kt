package com.example.voicerecorderlocation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceMarkerDao {
    @Query("SELECT * FROM place_markers WHERE sessionId = :sessionId ORDER BY elapsedMillis ASC")
    fun observeMarkers(sessionId: Long): Flow<List<PlaceMarkerEntity>>

    @Query("SELECT * FROM place_markers WHERE sessionId = :sessionId ORDER BY elapsedMillis ASC")
    suspend fun getMarkers(sessionId: Long): List<PlaceMarkerEntity>

    @Insert
    suspend fun insert(marker: PlaceMarkerEntity): Long

    @Insert
    suspend fun insertAll(markers: List<PlaceMarkerEntity>)

    @Query("DELETE FROM place_markers WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
