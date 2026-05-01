package com.example.voicerecorderlocation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording_sessions ORDER BY startedAtMillis DESC")
    fun observeSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<RecordingSessionEntity?>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getSession(id: Long): RecordingSessionEntity?

    @Insert
    suspend fun insert(session: RecordingSessionEntity): Long

    @Update
    suspend fun update(session: RecordingSessionEntity)

    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

