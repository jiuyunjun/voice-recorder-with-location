package com.example.voicerecorderlocation.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicerecorderlocation.data.LocationPointEntity
import com.example.voicerecorderlocation.data.PlaceMarkerEntity
import com.example.voicerecorderlocation.data.RecordingRepository
import com.example.voicerecorderlocation.data.RecordingSessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val session: RecordingSessionEntity? = null,
    val points: List<LocationPointEntity> = emptyList(),
    val markers: List<PlaceMarkerEntity> = emptyList()
)

class PlaybackViewModel(
    private val sessionId: Long,
    private val repository: RecordingRepository
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> =
        combine(
            repository.observeSession(sessionId),
            repository.observePoints(sessionId),
            repository.observeMarkers(sessionId)
        ) { session, points, markers ->
            PlaybackUiState(session, points, markers)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState())

    fun rename(title: String) {
        viewModelScope.launch { repository.renameSession(sessionId, title) }
    }

    companion object {
        fun factory(sessionId: Long, repository: RecordingRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaybackViewModel(sessionId, repository) as T
            }
    }
}
