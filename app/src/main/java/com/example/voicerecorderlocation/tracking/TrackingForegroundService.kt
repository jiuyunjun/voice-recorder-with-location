package com.example.voicerecorderlocation.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.voicerecorderlocation.LiveMarker
import com.example.voicerecorderlocation.R
import com.example.voicerecorderlocation.RecordingRuntimeState
import com.example.voicerecorderlocation.data.LocationPointEntity
import com.example.voicerecorderlocation.data.PlaceMarkerEntity
import com.example.voicerecorderlocation.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TrackingForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorder = AudioRecorder()
    private var activeSessionId: Long? = null
    private var locationJob: Job? = null
    private var amplitudeJob: Job? = null

    private var sessionStartedAt: Long = 0L
    @Volatile private var lastLocation: Location? = null
    private val waveformBuckets = ArrayList<Int>()
    private val liveMarkers = ArrayList<LiveMarker>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking()
            ACTION_MARK -> addMarker(intent.getStringExtra(EXTRA_MARKER_NAME).orEmpty())
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
                title = "录音 ${titleFormatter.format(Instant.ofEpochMilli(startedAt))}",
                audioPath = audioFile.absolutePath,
                startedAtMillis = startedAt
            )
            activeSessionId = sessionId
            sessionStartedAt = startedAt
            lastLocation = null
            waveformBuckets.clear()
            liveMarkers.clear()

            val recordingStarted = runCatching { recorder.start(audioFile) }.isSuccess
            if (!recordingStarted) {
                activeSessionId = null
                runCatching { ServiceLocator.repository.deleteSession(sessionId) }
                withContext(Dispatchers.Main) {
                    RecordingRuntimeState.isRecording = false
                    RecordingRuntimeState.locationStatus = "录音启动失败"
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            withContext(Dispatchers.Main) {
                RecordingRuntimeState.isRecording = true
                RecordingRuntimeState.startedAtMillis = startedAt
                RecordingRuntimeState.amplitudeLevel = 0f
                RecordingRuntimeState.locationStatus = "等待 GPS"
                RecordingRuntimeState.locationAccuracyMeters = null
                RecordingRuntimeState.pointCount = 0
                RecordingRuntimeState.activeSessionId = sessionId
                RecordingRuntimeState.markers = emptyList()
            }

            amplitudeJob = launch {
                while (true) {
                    val raw = runCatching { recorder.maxAmplitude() }.getOrDefault(0)
                    val level = raw.normalizedAmplitudeLevel()
                    waveformBuckets.add((level * 100f).toInt().coerceIn(0, 100))
                    withContext(Dispatchers.Main) { RecordingRuntimeState.amplitudeLevel = level }
                    delay(80)
                }
            }

            locationJob = launch {
                var lastAcceptedLocation: Location? = null
                var pointCount = 0
                val kalman = ImuAidedKalmanFilter(sigmaAccMs2 = 0.5)

                val gpsFlow = LocationSampler(applicationContext).locations()
                    .catch { e ->
                        if (e is SecurityException) withContext(Dispatchers.Main) {
                            RecordingRuntimeState.locationStatus = "位置权限被拒绝"
                        }
                    }.map { FusionEvent.Gps(it) }

                val imuFlow = ImuSampler(applicationContext).readings()
                    .catch { }.map { FusionEvent.Imu(it) }

                merge(gpsFlow, imuFlow).collect { event ->
                    when (event) {
                        is FusionEvent.Imu -> kalman.predictImu(
                            event.reading.aNorth, event.reading.aEast, event.reading.timestampNs
                        )
                        is FusionEvent.Gps -> {
                            val location = event.location
                            val rejectionReason = location.rejectionReason(lastAcceptedLocation)
                            if (rejectionReason != null) {
                                if (location.hasAccuracy() && location.accuracy <= MAX_ACCEPTED_ACCURACY_METERS) {
                                    kalman.updateGps(location.latitude, location.longitude, location.accuracy, location.time)
                                }
                                withContext(Dispatchers.Main) {
                                    RecordingRuntimeState.locationStatus = rejectionReason
                                    RecordingRuntimeState.locationAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                                }
                                return@collect
                            }
                            val accuracy = if (location.hasAccuracy()) location.accuracy else 20f
                            // Decompose GPS Doppler speed+bearing into North/East velocity for the filter.
                            val vNorth: Double?
                            val vEast: Double?
                            if (location.hasSpeed() && location.hasBearing() && location.speed > 0.3f) {
                                val b = Math.toRadians(location.bearing.toDouble())
                                val sp = location.speed.toDouble()
                                vNorth = sp * cos(b)
                                vEast = sp * sin(b)
                            } else {
                                vNorth = null; vEast = null
                            }
                            val speedAcc = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else 2f
                            val (filteredLat, filteredLng) = kalman.updateGps(
                                location.latitude, location.longitude, accuracy, location.time,
                                vNorth, vEast, speedAcc
                            )
                            val bearingDegrees = when {
                                location.hasBearing() -> location.bearing
                                lastAcceptedLocation != null && lastAcceptedLocation!!.distanceTo(location) >= MIN_BEARING_DISTANCE_METERS ->
                                    lastAcceptedLocation!!.bearingTo(location).normalizedBearing()
                                else -> null
                            }
                            ServiceLocator.repository.addLocation(
                                LocationPointEntity(
                                    sessionId = sessionId,
                                    latitude = filteredLat, longitude = filteredLng,
                                    accuracyMeters = accuracy,
                                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                                    speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                                    bearingDegrees = bearingDegrees,
                                    recordedAtMillis = location.time,
                                    elapsedRealtimeNanos = location.elapsedRealtimeNanos
                                )
                            )
                            pointCount += 1
                            lastLocation = location
                            withContext(Dispatchers.Main) {
                                RecordingRuntimeState.locationStatus = "已锁定"
                                RecordingRuntimeState.locationAccuracyMeters = accuracy
                                RecordingRuntimeState.pointCount = pointCount
                            }
                            lastAcceptedLocation = location
                        }
                    }
                }
            }
        }
    }

    private fun addMarker(name: String) {
        val sessionId = activeSessionId ?: return
        val now = System.currentTimeMillis()
        val elapsed = now - sessionStartedAt
        val loc = lastLocation
        val safeName = name.ifBlank { "标记 ${liveMarkers.size + 1}" }
        scope.launch {
            ServiceLocator.repository.addMarker(
                PlaceMarkerEntity(
                    sessionId = sessionId, name = safeName,
                    latitude = loc?.latitude, longitude = loc?.longitude,
                    elapsedMillis = elapsed, recordedAtMillis = now
                )
            )
        }
        liveMarkers.add(LiveMarker(safeName, elapsed))
        val snapshot = liveMarkers.toList()
        scope.launch(Dispatchers.Main) { RecordingRuntimeState.markers = snapshot }
    }

    private fun stopTracking() {
        val sessionId = activeSessionId ?: return
        locationJob?.cancel(); locationJob = null
        amplitudeJob?.cancel(); amplitudeJob = null
        recorder.stop()
        activeSessionId = null
        val waveformCsv = downsampleWaveform(waveformBuckets)
        scope.launch(Dispatchers.Main) {
            RecordingRuntimeState.isRecording = false
            RecordingRuntimeState.startedAtMillis = null
            RecordingRuntimeState.amplitudeLevel = 0f
            RecordingRuntimeState.locationStatus = "等待 GPS"
            RecordingRuntimeState.locationAccuracyMeters = null
            RecordingRuntimeState.pointCount = 0
            RecordingRuntimeState.activeSessionId = null
            RecordingRuntimeState.markers = emptyList()
        }
        scope.launch {
            ServiceLocator.repository.finishSession(sessionId, System.currentTimeMillis())
            if (waveformCsv.isNotEmpty()) runCatching { ServiceLocator.repository.saveWaveform(sessionId, waveformCsv) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun downsampleWaveform(src: List<Int>, target: Int = 160): String {
        if (src.isEmpty()) return ""
        if (src.size <= target) return src.joinToString(",")
        val step = src.size.toFloat() / target
        return (0 until target).joinToString(",") { i ->
            val start = (i * step).toInt()
            val end = ((i + 1) * step).toInt().coerceAtMost(src.size)
            src.subList(start, end.coerceAtLeast(start + 1)).max().toString()
        }
    }

    private fun createAudioFile(startedAt: Long): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        return File(directory, "recording-$startedAt.m4a")
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setOngoing(true).build()

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1001
        // Must exceed typical GPS noise (~3–5 m) or the heading jitters while still.
        private const val MIN_BEARING_DISTANCE_METERS = 4f
        const val MAX_ACCEPTED_ACCURACY_METERS = 20f
        const val FIRST_FIX_ACCURACY_METERS = 150f
        const val MIN_ACCEPTED_INTERVAL_MILLIS = 2_000L
        const val MIN_ACCEPTED_DISTANCE_METERS = 3f
        const val MIN_ACCURACY_IMPROVEMENT_METERS = 5f
        const val MAX_REASONABLE_SPEED_METERS_PER_SECOND = 60f
        private const val ACTION_START = "com.example.voicerecorderlocation.START_TRACKING"
        private const val ACTION_STOP  = "com.example.voicerecorderlocation.STOP_TRACKING"
        private const val ACTION_MARK  = "com.example.voicerecorderlocation.MARK_PLACE"
        private const val EXTRA_MARKER_NAME = "marker_name"
        private val titleFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

        fun startIntent(context: Context): Intent =
            Intent(context, TrackingForegroundService::class.java).setAction(ACTION_START)
        fun stopIntent(context: Context): Intent =
            Intent(context, TrackingForegroundService::class.java).setAction(ACTION_STOP)
        fun markIntent(context: Context, name: String): Intent =
            Intent(context, TrackingForegroundService::class.java).setAction(ACTION_MARK).putExtra(EXTRA_MARKER_NAME, name)
    }
}

private sealed class FusionEvent {
    data class Gps(val location: Location) : FusionEvent()
    data class Imu(val reading: ImuReading) : FusionEvent()
}

private fun Float.normalizedBearing(): Float = (this + 360f) % 360f

private fun Location.rejectionReason(prev: Location?): String? {
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return "坐标无效"
    if (!hasAccuracy()) return "精度不足"
    // First fix uses a relaxed threshold so the map appears quickly; later points
    // are filtered strictly so the track stays clean once GPS has locked on.
    val maxAccuracy = if (prev == null) TrackingForegroundService.FIRST_FIX_ACCURACY_METERS
        else TrackingForegroundService.MAX_ACCEPTED_ACCURACY_METERS
    if (accuracy > maxAccuracy) return "精度不足 ±${accuracy.toInt()} 米"
    if (prev == null) return null
    if (time > 0L && prev.time > 0L && time <= prev.time) return "位置数据过期"
    val elapsedMs = if (time > 0L && prev.time > 0L) time - prev.time else Long.MAX_VALUE
    val dist = prev.distanceTo(this)
    val speed = if (elapsedMs in 1 until Long.MAX_VALUE) dist / (elapsedMs / 1_000f) else 0f
    if (speed > TrackingForegroundService.MAX_REASONABLE_SPEED_METERS_PER_SECOND) return "GPS 跳点已过滤"
    val improved = prev.hasAccuracy() && prev.accuracy - accuracy >= TrackingForegroundService.MIN_ACCURACY_IMPROVEMENT_METERS
    return if (elapsedMs >= TrackingForegroundService.MIN_ACCEPTED_INTERVAL_MILLIS ||
        dist >= TrackingForegroundService.MIN_ACCEPTED_DISTANCE_METERS || improved) null else "位置保持中"
}

private fun Int.normalizedAmplitudeLevel(): Float {
    if (this <= 0) return 0f
    return sqrt((this.coerceAtMost(MAX_AMPLITUDE).toFloat() / MAX_AMPLITUDE).toDouble()).toFloat()
}

private const val MAX_AMPLITUDE = 32_767
