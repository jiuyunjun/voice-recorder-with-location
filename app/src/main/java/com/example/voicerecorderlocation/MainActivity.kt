package com.example.voicerecorderlocation

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.Locale

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
    var isRecording by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            startForegroundService(context, TrackingForegroundService.startIntent(context))
            isRecording = true
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
                    isRecording = false
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
        viewModel.importAudio(uris)
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
                Button(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                    Text("Import")
                }
                Button(
                    onClick = {
                        selectedIds = if (selectedIds.isEmpty()) {
                            sessions.map { it.id }.toSet()
                        } else {
                            emptySet()
                        }
                    },
                    enabled = sessions.isNotEmpty()
                ) {
                    Text(if (selectedIds.isEmpty()) "Select" else "Clear")
                }
            }
            if (selectedIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shareSessions(context, selectedSessions) }) {
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
                onOpen = { onOpen(session.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(
    session: RecordingSessionEntity,
    selected: Boolean,
    selecting: Boolean,
    onToggleSelected: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        onClick = { if (selecting) onToggleSelected() else onOpen() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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

    DisposableEffect(player) {
        onDispose {
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
        RouteMap(points = state.points, progressMillis = progress.toLong(), mapType = mapType)
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
        val currentPoint = pointAtProgress(state.points, progress.toLong())
        Text("Direction ${formatBearing(currentPoint?.bearingDegrees)}")
        Text("${formatMillis(progress.toLong())} / ${formatMillis(player?.duration?.toLong() ?: 0)}")
    }
}

@Composable
private fun RouteMap(
    points: List<LocationPointEntity>,
    progressMillis: Long,
    mapType: MapType
) {
    val context = LocalContext.current
    val first = points.firstOrNull()
    val route = points.map { LatLng(it.latitude, it.longitude) }
    val directionIcon = remember {
        bitmapDescriptorFromVector(context, R.drawable.ic_direction_arrow)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(first?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(35.6812, 139.7671), 15f)
    }

    LaunchedEffect(first?.id) {
        if (first != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15f))
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
        val current = pointAtProgress(points, progressMillis)
        current?.let {
            Marker(
                state = rememberUpdatedMarkerState(LatLng(it.latitude, it.longitude)),
                title = "Current",
                snippet = "Direction ${formatBearing(it.bearingDegrees)}",
                icon = directionIcon,
                flat = true,
                anchor = Offset(0.5f, 0.5f),
                rotation = it.bearingDegrees ?: 0f
            )
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

    fun importAudio(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            uris.forEach { uri ->
                val importedAt = System.currentTimeMillis()
                val displayName = context.displayName(uri) ?: "recording-$importedAt.m4a"
                val target = context.createImportedAudioFile(displayName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@forEach
                repository.createImportedSession(
                    title = displayName.substringBeforeLast('.'),
                    audioPath = target.absolutePath,
                    importedAtMillis = importedAt,
                    durationMillis = context.audioDurationMillis(uri)
                )
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

private fun pointAtProgress(points: List<LocationPointEntity>, progressMillis: Long): LocationPointEntity? {
    val firstTime = points.firstOrNull()?.recordedAtMillis ?: return null
    val targetTime = firstTime + progressMillis
    return points.lastOrNull { it.recordedAtMillis <= targetTime } ?: points.firstOrNull()
}

private fun shareSessions(context: Context, sessions: List<RecordingSessionEntity>) {
    val uris = sessions.mapNotNull { session ->
        val file = File(session.audioPath)
        if (!file.exists()) return@mapNotNull null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    if (uris.isEmpty()) return

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recordings"))
}

private fun Context.displayName(uri: Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            cursor.getString(0)
        } else {
            null
        }
    } finally {
        cursor?.close()
    }
}

private fun Context.createImportedAudioFile(displayName: String): File {
    val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
    directory.mkdirs()
    val cleanName = displayName
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "imported-recording.m4a" }
    return File(directory, "${UUID.randomUUID()}-$cleanName")
}

private fun Context.audioDurationMillis(uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(this, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        retriever.release()
    }
}

private fun bitmapDescriptorFromVector(context: Context, drawableResId: Int): BitmapDescriptor {
    val drawable = requireNotNull(ContextCompat.getDrawable(context, drawableResId))
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
