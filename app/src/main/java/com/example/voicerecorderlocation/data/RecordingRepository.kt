package com.example.voicerecorderlocation.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(
    private val recordingDao: RecordingDao,
    private val locationPointDao: LocationPointDao
) {
    fun observeSessions(): Flow<List<RecordingSessionEntity>> = recordingDao.observeSessions()

    fun observeSession(id: Long): Flow<RecordingSessionEntity?> = recordingDao.observeSession(id)

    fun observePoints(sessionId: Long): Flow<List<LocationPointEntity>> =
        locationPointDao.observePoints(sessionId)

    suspend fun getPoints(sessionId: Long): List<LocationPointEntity> =
        locationPointDao.getPoints(sessionId)

    suspend fun createSession(title: String, audioPath: String, startedAtMillis: Long): Long =
        recordingDao.insert(
            RecordingSessionEntity(
                title = title,
                audioPath = audioPath,
                startedAtMillis = startedAtMillis
            )
        )

    suspend fun createImportedSession(
        title: String,
        audioPath: String,
        startedAtMillis: Long,
        endedAtMillis: Long?,
        durationMillis: Long
    ): Long =
        recordingDao.insert(
            RecordingSessionEntity(
                title = title,
                audioPath = audioPath,
                startedAtMillis = startedAtMillis,
                endedAtMillis = endedAtMillis,
                durationMillis = durationMillis
            )
        )

    suspend fun finishSession(id: Long, endedAtMillis: Long) {
        val current = recordingDao.getSession(id) ?: return
        recordingDao.update(
            current.copy(
                endedAtMillis = endedAtMillis,
                durationMillis = endedAtMillis - current.startedAtMillis
            )
        )
    }

    suspend fun addLocation(point: LocationPointEntity) {
        locationPointDao.insert(point)
    }

    suspend fun addLocations(points: List<LocationPointEntity>) {
        if (points.isNotEmpty()) {
            locationPointDao.insertAll(points)
        }
    }

    suspend fun deleteSession(id: Long) {
        locationPointDao.deleteForSession(id)
        recordingDao.delete(id)
    }
}
