package com.example.voicerecorderlocation.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(
    private val recordingDao: RecordingDao,
    private val locationPointDao: LocationPointDao,
    private val placeMarkerDao: PlaceMarkerDao
) {
    fun observeSessions(): Flow<List<RecordingSessionEntity>> = recordingDao.observeSessions()
    fun observeSession(id: Long): Flow<RecordingSessionEntity?> = recordingDao.observeSession(id)
    fun observePoints(sessionId: Long): Flow<List<LocationPointEntity>> = locationPointDao.observePoints(sessionId)
    fun observeMarkers(sessionId: Long): Flow<List<PlaceMarkerEntity>> = placeMarkerDao.observeMarkers(sessionId)

    suspend fun getPoints(sessionId: Long): List<LocationPointEntity> = locationPointDao.getPoints(sessionId)
    suspend fun getMarkers(sessionId: Long): List<PlaceMarkerEntity> = placeMarkerDao.getMarkers(sessionId)

    suspend fun createSession(title: String, audioPath: String, startedAtMillis: Long): Long =
        recordingDao.insert(RecordingSessionEntity(title = title, audioPath = audioPath, startedAtMillis = startedAtMillis))

    suspend fun createImportedSession(
        title: String, audioPath: String, startedAtMillis: Long, endedAtMillis: Long?, durationMillis: Long
    ): Long = recordingDao.insert(
        RecordingSessionEntity(title = title, audioPath = audioPath, startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis, durationMillis = durationMillis)
    )

    suspend fun finishSession(id: Long, endedAtMillis: Long) {
        val current = recordingDao.getSession(id) ?: return
        recordingDao.update(current.copy(endedAtMillis = endedAtMillis, durationMillis = endedAtMillis - current.startedAtMillis))
    }

    suspend fun renameSession(id: Long, title: String) {
        val current = recordingDao.getSession(id) ?: return
        if (title.isBlank()) return
        recordingDao.update(current.copy(title = title.trim()))
    }

    suspend fun saveWaveform(id: Long, waveformCsv: String) {
        val current = recordingDao.getSession(id) ?: return
        recordingDao.update(current.copy(waveform = waveformCsv))
    }

    suspend fun addLocation(point: LocationPointEntity) = locationPointDao.insert(point)

    suspend fun addLocations(points: List<LocationPointEntity>) {
        if (points.isNotEmpty()) locationPointDao.insertAll(points)
    }

    suspend fun addMarker(marker: PlaceMarkerEntity): Long = placeMarkerDao.insert(marker)

    suspend fun addMarkers(markers: List<PlaceMarkerEntity>) {
        if (markers.isNotEmpty()) placeMarkerDao.insertAll(markers)
    }

    suspend fun deleteSession(id: Long) {
        placeMarkerDao.deleteForSession(id)
        locationPointDao.deleteForSession(id)
        recordingDao.delete(id)
    }
}
