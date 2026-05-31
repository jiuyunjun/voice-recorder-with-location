package com.example.voicerecorderlocation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class LiveMarker(val name: String, val elapsedMillis: Long)

object RecordingRuntimeState {
    var isRecording by mutableStateOf(false)
    var startedAtMillis by mutableStateOf<Long?>(null)
    var amplitudeLevel by mutableFloatStateOf(0f)
    var locationStatus by mutableStateOf("等待 GPS")
    var locationAccuracyMeters by mutableStateOf<Float?>(null)
    var currentSpeedMps by mutableStateOf<Float?>(null)
    var pointCount by mutableStateOf(0)
    var activeSessionId by mutableStateOf<Long?>(null)
    var markers by mutableStateOf<List<LiveMarker>>(emptyList())
}
