package com.example.voicerecorderlocation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {
    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY recordedAtMillis ASC")
    fun observePoints(sessionId: Long): Flow<List<LocationPointEntity>>

    @Insert
    suspend fun insert(point: LocationPointEntity)

    @Query("DELETE FROM location_points WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}

