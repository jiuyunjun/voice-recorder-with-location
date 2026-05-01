package com.example.voicerecorderlocation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
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
                    icon = { Text("●") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("sessions") },
                    label = { Text("Sessions") },
                    icon = { Text("≡") }
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
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Sessions", style = MaterialTheme.typography.headlineMedium)
        }
        items(sessions, key = { it.id }) { session ->
            SessionCard(session = session, onOpen = { onOpen(session.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(session: RecordingSessionEntity, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium)
            Text("Duration ${formatMillis(session.durationMillis)}")
            Text(File(session.audioPath).name)
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
        Text("${formatMillis(progress.toLong())} / ${formatMillis(player?.duration?.toLong() ?: 0)}")
    }
}

@Composable
private fun RouteMap(
    points: List<LocationPointEntity>,
    progressMillis: Long,
    mapType: MapType
) {
    val first = points.firstOrNull()
    val route = points.map { LatLng(it.latitude, it.longitude) }
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
                title = "Current"
            )
        }
    }
}

class RecordingListViewModel(
    repository: RecordingRepository = ServiceLocator.repository
) : ViewModel() {
    val sessions: StateFlow<List<RecordingSessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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

private fun pointAtProgress(points: List<LocationPointEntity>, progressMillis: Long): LocationPointEntity? {
    val firstTime = points.firstOrNull()?.recordedAtMillis ?: return null
    val targetTime = firstTime + progressMillis
    return points.lastOrNull { it.recordedAtMillis <= targetTime } ?: points.firstOrNull()
}
