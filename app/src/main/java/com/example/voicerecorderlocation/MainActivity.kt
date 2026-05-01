package com.example.voicerecorderlocation

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.voicerecorderlocation.data.LocationPointEntity
import com.example.voicerecorderlocation.data.RecordingRepository
import com.example.voicerecorderlocation.data.RecordingSessionEntity
import com.example.voicerecorderlocation.di.ServiceLocator
import com.example.voicerecorderlocation.tracking.TrackingForegroundService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceRecorderApp()
                }
            }
        }
    }
}

private object RecordingRuntimeState {
    var isRecording by mutableStateOf(false)
}

@Composable
private fun VoiceRecorderApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("record") },
                    label = { Text("Record") },
                    icon = { Text("Rec") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("sessions") },
                    label = { Text("Sessions") },
                    icon = { Text("List") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "record",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("record") { RecordingScreen() }
            composable("sessions") {
                RecordingListScreen(onOpen = { navController.navigate("playback/$it") })
            }
            composable(
                route = "playback/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
                PlaybackScreen(sessionId = sessionId)
            }
        }
    }
}

@Composable
private fun RecordingScreen() {
    val context = LocalContext.current
    val isRecording = RecordingRuntimeState.isRecording
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            startForegroundService(context, TrackingForegroundService.startIntent(context))
            RecordingRuntimeState.isRecording = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Recording", style = MaterialTheme.typography.headlineMedium)
        Text("Foreground service records audio and location continuously, including while the app is in the background.")
        Button(
            onClick = {
                if (isRecording) {
                    context.startService(TrackingForegroundService.stopIntent(context))
                    RecordingRuntimeState.isRecording = false
                } else {
                    permissionLauncher.launch(requiredRuntimePermissions())
                }
            }
        ) {
            Text(if (isRecording) "Stop" else "Start")
        }
        Button(onClick = { context.openAppSettings() }) {
            Text("Open app settings for background location")
        }
    }
}

@Composable
private fun RecordingListScreen(
    onOpen: (Long) -> Unit,
    viewModel: RecordingListViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.importZipArchives(uris)
    }
    val selectedSessions = sessions.filter { it.id in selectedIds }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Sessions", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                    Text("Import")
                }
                Button(
                    onClick = { selectedIds = emptySet() },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Clear")
                }
            }
            if (selectedIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.exportAndShare(context, selectedSessions) }) {
                        Text("Share")
                    }
                    Button(
                        onClick = {
                            viewModel.deleteSessions(selectedSessions)
                            selectedIds = emptySet()
                        }
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
        items(sessions, key = { it.id }) { session ->
            val selected = session.id in selectedIds
            SessionCard(
                session = session,
                selected = selected,
                selecting = selectedIds.isNotEmpty(),
                onToggleSelected = {
                    selectedIds = if (selected) {
                        selectedIds - session.id
                    } else {
                        selectedIds + session.id
                    }
                },
                onStartSelection = { selectedIds = selectedIds + session.id },
                onOpen = { onOpen(session.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: RecordingSessionEntity,
    selected: Boolean,
    selecting: Boolean,
    onToggleSelected: () -> Unit,
    onStartSelection: () -> Unit,
    onOpen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = { if (selecting) onToggleSelected() else onOpen() },
                    onLongClick = onStartSelection
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            }
            Column {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                Text("Duration ${formatMillis(session.durationMillis)}")
                Text(File(session.audioPath).name)
            }
        }
    }
}

@Composable
private fun PlaybackScreen(sessionId: Long) {
    val viewModel: PlaybackViewModel = viewModel(
        factory = PlaybackViewModel.factory(sessionId, ServiceLocator.repository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val player = remember(state.session?.audioPath) {
        state.session?.audioPath?.let { android.media.MediaPlayer().apply { setDataSource(it); prepare() } }
    }
    var progress by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }

    LaunchedEffect(player) {
        player?.setOnCompletionListener {
            progress = it.duration.toFloat()
            playing = false
        }
    }
    DisposableEffect(player) {
        onDispose {
            player?.setOnCompletionListener(null)
            player?.release()
        }
    }
    LaunchedEffect(player, playing) {
        while (playing && player != null) {
            progress = player.currentPosition.toFloat()
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(state.session?.title ?: "Playback", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { mapType = MapType.NORMAL }, enabled = mapType != MapType.NORMAL) {
                Text("Map")
            }
            Button(onClick = { mapType = MapType.SATELLITE }, enabled = mapType != MapType.SATELLITE) {
                Text("Satellite")
            }
        }
        RouteMap(
            points = state.points,
            sessionStartMillis = state.session?.startedAtMillis,
            progressMillis = progress.toLong(),
            mapType = mapType
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val current = player ?: return@Button
                    if (playing) {
                        current.pause()
                    } else {
                        current.start()
                    }
                    playing = !playing
                },
                enabled = player != null
            ) {
                Text(if (playing) "Pause" else "Play")
            }
            Button(onClick = { context.openAppSettings() }) {
                Text("Map key")
            }
        }
        Slider(
            value = progress,
            onValueChange = {
                progress = it
                player?.seekTo(it.toInt())
            },
            valueRange = 0f..(player?.duration?.toFloat() ?: 1f)
        )
        val currentLocation = locationAtProgress(
            points = state.points,
            sessionStartMillis = state.session?.startedAtMillis,
            progressMillis = progress.toLong()
        )
        Text("Direction ${formatBearing(currentLocation?.bearingDegrees)}")
        Text("${formatMillis(progress.toLong())} / ${formatMillis(player?.duration?.toLong() ?: 0)}")
    }
}

@Composable
private fun RouteMap(
    points: List<LocationPointEntity>,
    sessionStartMillis: Long?,
    progressMillis: Long,
    mapType: MapType
) {
    val first = points.firstOrNull()
    val route = points.map { LatLng(it.latitude, it.longitude) }
    val current = locationAtProgress(points, sessionStartMillis, progressMillis)
    val currentLatLng = current?.let { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(first?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(35.6812, 139.7671), 15f)
    }

    LaunchedEffect(first?.id) {
        if (first != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15f))
        }
    }
    LaunchedEffect(currentLatLng) {
        if (currentLatLng != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(currentLatLng))
        }
    }

    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = mapType)
    ) {
        if (route.size >= 2) {
            Polyline(points = route)
        }
        first?.let {
            Marker(
                state = rememberUpdatedMarkerState(LatLng(it.latitude, it.longitude)),
                title = "Start"
            )
        }
        current?.let {
            Marker(
                state = rememberUpdatedMarkerState(LatLng(it.latitude, it.longitude)),
                title = "Current",
                snippet = "Direction ${formatBearing(it.bearingDegrees)}"
            )
            directionSegment(LatLng(it.latitude, it.longitude), it.bearingDegrees)?.let { segment ->
                Polyline(points = segment, width = 8f)
            }
        }
    }
}

class RecordingListViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository: RecordingRepository = ServiceLocator.repository

    val sessions: StateFlow<List<RecordingSessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteSessions(sessions: List<RecordingSessionEntity>) {
        viewModelScope.launch {
            sessions.forEach { session ->
                File(session.audioPath).delete()
                repository.deleteSession(session.id)
            }
        }
    }

    fun exportAndShare(shareContext: Context, sessions: List<RecordingSessionEntity>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val zipFiles = withContext(Dispatchers.IO) {
                sessions.mapNotNull { session ->
                    val points = repository.getPoints(session.id)
                    context.exportSessionZip(session, points)
                }
            }
            shareZipFiles(shareContext, zipFiles)
        }
    }

    fun importZipArchives(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val imported = context.importSessionZip(uri) ?: return@forEach
                    val sessionId = repository.createImportedSession(
                        title = imported.title,
                        audioPath = imported.audioFile.absolutePath,
                        startedAtMillis = imported.startedAtMillis,
                        endedAtMillis = imported.endedAtMillis,
                        durationMillis = imported.durationMillis
                    )
                    repository.addLocations(
                        imported.points.map { point ->
                            point.copy(id = 0, sessionId = sessionId)
                        }
                    )
                }
            }
        }
    }
}

data class PlaybackUiState(
    val session: RecordingSessionEntity? = null,
    val points: List<LocationPointEntity> = emptyList()
)

class PlaybackViewModel(
    sessionId: Long,
    repository: RecordingRepository
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> =
        combine(repository.observeSession(sessionId), repository.observePoints(sessionId)) { session, points ->
            PlaybackUiState(session = session, points = points)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState())

    companion object {
        fun factory(sessionId: Long, repository: RecordingRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaybackViewModel(sessionId, repository) as T
            }
    }
}

private fun requiredRuntimePermissions(): Array<String> =
    buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    startActivity(intent)
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatBearing(bearingDegrees: Float?): String =
    bearingDegrees?.let { "${it.toInt()} deg" } ?: "unknown"

private data class PlaybackLocation(
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float?
)

private fun locationAtProgress(
    points: List<LocationPointEntity>,
    sessionStartMillis: Long?,
    progressMillis: Long
): PlaybackLocation? {
    if (points.isEmpty()) return null
    val targetTime = (sessionStartMillis ?: points.first().recordedAtMillis) + progressMillis
    val nextIndex = points.indexOfFirst { it.recordedAtMillis >= targetTime }

    if (nextIndex <= 0) {
        return points.first().toPlaybackLocation()
    }
    if (nextIndex == -1) {
        return points.last().toPlaybackLocation()
    }

    val previous = points[nextIndex - 1]
    val next = points[nextIndex]
    val spanMillis = next.recordedAtMillis - previous.recordedAtMillis
    if (spanMillis <= 0L) return next.toPlaybackLocation()

    val fraction = (targetTime - previous.recordedAtMillis).toDouble() / spanMillis.toDouble()
    return PlaybackLocation(
        latitude = previous.latitude + (next.latitude - previous.latitude) * fraction,
        longitude = previous.longitude + (next.longitude - previous.longitude) * fraction,
        bearingDegrees = previous.bearingDegrees ?: next.bearingDegrees
    )
}

private fun LocationPointEntity.toPlaybackLocation(): PlaybackLocation {
    return PlaybackLocation(
        latitude = latitude,
        longitude = longitude,
        bearingDegrees = bearingDegrees
    )
}

private fun directionSegment(origin: LatLng, bearingDegrees: Float?): List<LatLng>? {
    val bearing = bearingDegrees ?: return null
    return listOf(origin, destinationPoint(origin, bearing, 40.0))
}

private fun destinationPoint(origin: LatLng, bearingDegrees: Float, distanceMeters: Double): LatLng {
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val bearing = bearingDegrees.toDouble().degreesToRadians()
    val lat1 = origin.latitude.degreesToRadians()
    val lon1 = origin.longitude.degreesToRadians()

    val lat2 = asin(
        sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearing)
    )
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2)
    )

    return LatLng(lat2 * 180.0 / PI, lon2 * 180.0 / PI)
}

private data class ImportedSessionArchive(
    val title: String,
    val audioFile: File,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val durationMillis: Long,
    val points: List<LocationPointEntity>
)

private fun shareZipFiles(context: Context, zipFiles: List<File>) {
    val uris = zipFiles.mapNotNull { file ->
        if (!file.exists()) return@mapNotNull null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    if (uris.isEmpty()) return

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/zip"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording archives"))
}

private fun Context.exportSessionZip(
    session: RecordingSessionEntity,
    points: List<LocationPointEntity>
): File? {
    val audioFile = File(session.audioPath)
    if (!audioFile.exists()) return null

    val exportDirectory = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir, "Exports")
    exportDirectory.mkdirs()
    val safeTitle = session.title.sanitizedFileName().ifBlank { "recording-${session.id}" }
    val zipFile = File(exportDirectory, "$safeTitle-${session.id}.zip")
    val audioEntryName = "recording.${audioFile.extension.ifBlank { "m4a" }}"
    val metadata = sessionArchiveMetadata(session, points, audioEntryName)
    val geoJson = sessionTrackGeoJson(session, points)

    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
        zip.putNextEntry(ZipEntry(audioEntryName))
        audioFile.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("metadata.json"))
        zip.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("track.geojson"))
        zip.write(geoJson.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return zipFile
}

private fun Context.importSessionZip(uri: Uri): ImportedSessionArchive? {
    var metadata: JSONObject? = null
    var audioFile: File? = null

    contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when {
                        entry.name == "metadata.json" -> {
                            metadata = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        entry.name.startsWith("recording.") -> {
                            val target = createImportedAudioFile(entry.name)
                            FileOutputStream(target).use { output -> zip.copyTo(output) }
                            audioFile = target
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    val archiveMetadata = metadata ?: return null
    val importedAudioFile = audioFile ?: return null
    return ImportedSessionArchive(
        title = archiveMetadata.optString("title", importedAudioFile.nameWithoutExtension),
        audioFile = importedAudioFile,
        startedAtMillis = archiveMetadata.optLong("startedAtMillis", System.currentTimeMillis()),
        endedAtMillis = archiveMetadata.nullableLong("endedAtMillis"),
        durationMillis = archiveMetadata.optLong("durationMillis", 0L),
        points = archiveMetadata.pointsFromMetadata()
    )
}

private fun Context.createImportedAudioFile(displayName: String): File {
    val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
    directory.mkdirs()
    val cleanName = displayName
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "imported-recording.m4a" }
    return File(directory, "${UUID.randomUUID()}-$cleanName")
}

private fun sessionArchiveMetadata(
    session: RecordingSessionEntity,
    points: List<LocationPointEntity>,
    audioEntryName: String
): JSONObject =
    JSONObject()
        .put("format", "voice-recorder-with-location")
        .put("version", 1)
        .put("title", session.title)
        .put("audioEntry", audioEntryName)
        .put("startedAtMillis", session.startedAtMillis)
        .put("endedAtMillis", session.endedAtMillis ?: JSONObject.NULL)
        .put("durationMillis", session.durationMillis)
        .put("points", JSONArray(points.map { it.toJson() }))

private fun sessionTrackGeoJson(
    session: RecordingSessionEntity,
    points: List<LocationPointEntity>
): JSONObject =
    JSONObject()
        .put("type", "FeatureCollection")
        .put(
            "features",
            JSONArray(
                listOf(
                    JSONObject()
                        .put("type", "Feature")
                        .put(
                            "properties",
                            JSONObject()
                                .put("title", session.title)
                                .put("startedAtMillis", session.startedAtMillis)
                        )
                        .put(
                            "geometry",
                            JSONObject()
                                .put("type", "LineString")
                                .put(
                                    "coordinates",
                                    JSONArray(points.map { JSONArray(listOf(it.longitude, it.latitude)) })
                                )
                        )
                )
            )
        )

private fun LocationPointEntity.toJson(): JSONObject =
    JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("accuracyMeters", accuracyMeters ?: JSONObject.NULL)
        .put("altitudeMeters", altitudeMeters ?: JSONObject.NULL)
        .put("speedMetersPerSecond", speedMetersPerSecond ?: JSONObject.NULL)
        .put("bearingDegrees", bearingDegrees ?: JSONObject.NULL)
        .put("recordedAtMillis", recordedAtMillis)
        .put("elapsedRealtimeNanos", elapsedRealtimeNanos)

private fun JSONObject.pointsFromMetadata(): List<LocationPointEntity> {
    val points = optJSONArray("points") ?: return emptyList()
    return List(points.length()) { index ->
        val point = points.getJSONObject(index)
        LocationPointEntity(
            sessionId = 0,
            latitude = point.getDouble("latitude"),
            longitude = point.getDouble("longitude"),
            accuracyMeters = point.nullableDouble("accuracyMeters")?.toFloat(),
            altitudeMeters = point.nullableDouble("altitudeMeters"),
            speedMetersPerSecond = point.nullableDouble("speedMetersPerSecond")?.toFloat(),
            bearingDegrees = point.nullableDouble("bearingDegrees")?.toFloat(),
            recordedAtMillis = point.getLong("recordedAtMillis"),
            elapsedRealtimeNanos = point.optLong("elapsedRealtimeNanos", 0L)
        )
    }
}

private const val EARTH_RADIUS_METERS = 6_371_000.0

private fun Double.degreesToRadians(): Double = this * PI / 180.0

private fun JSONObject.nullableLong(name: String): Long? =
    if (isNull(name)) null else getLong(name)

private fun JSONObject.nullableDouble(name: String): Double? =
    if (isNull(name)) null else getDouble(name)

private fun String.sanitizedFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_")
