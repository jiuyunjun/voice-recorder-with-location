package com.example.voicerecorderlocation.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.voicerecorderlocation.R
import com.example.voicerecorderlocation.data.LocationPointEntity
import com.example.voicerecorderlocation.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TrackingForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorder = AudioRecorder()
    private var activeSessionId: Long? = null
    private var locationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking()
            else -> startTracking()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    private fun startTracking() {
        if (activeSessionId != null) return
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        scope.launch {
            val startedAt = System.currentTimeMillis()
            val audioFile = createAudioFile(startedAt)
            val sessionId = ServiceLocator.repository.createSession(
                title = "Recording ${titleFormatter.format(Instant.ofEpochMilli(startedAt))}",
                audioPath = audioFile.absolutePath,
                startedAtMillis = startedAt
            )
            activeSessionId = sessionId
            recorder.start(audioFile)
            locationJob = launch {
                var lastLocation: android.location.Location? = null
                LocationSampler(applicationContext).locations()
                    .catch { /* Keep recording audio even when location updates are unavailable. */ }
                    .collect { location ->
                        val previousLocation = lastLocation
                        val bearingDegrees = when {
                            location.hasBearing() -> location.bearing
                            previousLocation != null && previousLocation.distanceTo(location) >= MIN_BEARING_DISTANCE_METERS ->
                                previousLocation.bearingTo(location).normalizedBearing()
                            else -> null
                        }
                        ServiceLocator.repository.addLocation(
                            LocationPointEntity(
                                sessionId = sessionId,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                                speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                                bearingDegrees = bearingDegrees,
                                recordedAtMillis = location.time,
                                elapsedRealtimeNanos = location.elapsedRealtimeNanos
                            )
                        )
                        lastLocation = location
                    }
            }
        }
    }

    private fun stopTracking() {
        val sessionId = activeSessionId ?: return
        locationJob?.cancel()
        locationJob = null
        recorder.stop()
        activeSessionId = null
        scope.launch {
            ServiceLocator.repository.finishSession(sessionId, System.currentTimeMillis())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createAudioFile(startedAt: Long): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        return File(directory, "recording-$startedAt.m4a")
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_BEARING_DISTANCE_METERS = 1f
        private const val ACTION_START = "com.example.voicerecorderlocation.START_TRACKING"
        private const val ACTION_STOP = "com.example.voicerecorderlocation.STOP_TRACKING"
        private val titleFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())

        fun startIntent(context: Context): Intent =
            Intent(context, TrackingForegroundService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, TrackingForegroundService::class.java).setAction(ACTION_STOP)
    }
}

private fun Float.normalizedBearing(): Float = (this + 360f) % 360f
