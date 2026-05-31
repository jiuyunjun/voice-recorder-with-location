package com.example.voicerecorderlocation.ui.sessions

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicerecorderlocation.data.RecordingRepository
import com.example.voicerecorderlocation.data.RecordingSessionEntity
import com.example.voicerecorderlocation.di.ServiceLocator
import com.example.voicerecorderlocation.util.exportSessionZip
import com.example.voicerecorderlocation.util.importSessionZip
import com.example.voicerecorderlocation.util.shareZipFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecordingListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RecordingRepository = ServiceLocator.repository

    val sessions: StateFlow<List<RecordingSessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    fun clearImportError() {
        _importError.value = null
    }

    fun deleteSessions(sessions: List<RecordingSessionEntity>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                sessions.forEach { session ->
                    runCatching { File(session.audioPath).delete() }
                    repository.deleteSession(session.id)
                }
            }
        }
    }

    fun exportAndShare(shareContext: Context, sessions: List<RecordingSessionEntity>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val zipFiles = withContext(Dispatchers.IO) {
                sessions.mapNotNull { session ->
                    val points = repository.getPoints(session.id)
                    val markers = repository.getMarkers(session.id)
                    context.exportSessionZip(session, points, markers)
                }
            }
            shareZipFiles(shareContext, zipFiles)
        }
    }

    fun importZipArchives(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            var failCount = 0
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val imported = context.importSessionZip(uri)
                    if (imported == null) {
                        failCount++
                        return@forEach
                    }
                    val sessionId = repository.createImportedSession(
                        title = imported.title,
                        audioPath = imported.audioFile.absolutePath,
                        startedAtMillis = imported.startedAtMillis,
                        endedAtMillis = imported.endedAtMillis,
                        durationMillis = imported.durationMillis
                    )
                    imported.waveform?.let { repository.saveWaveform(sessionId, it) }
                    repository.addLocations(
                        imported.points.map { point -> point.copy(id = 0, sessionId = sessionId) }
                    )
                    repository.addMarkers(
                        imported.markers.map { marker -> marker.copy(id = 0, sessionId = sessionId) }
                    )
                }
            }
            if (failCount > 0) {
                _importError.value = "导入失败 $failCount 个文件，文件可能已损坏或格式不支持。"
            }
        }
    }
}
