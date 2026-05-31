package com.example.voicerecorderlocation.ui.playback

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import com.example.voicerecorderlocation.ui.components.FastForwardIcon
import com.example.voicerecorderlocation.ui.components.FastRewindIcon
import com.example.voicerecorderlocation.ui.components.PauseIcon
import com.example.voicerecorderlocation.ui.components.PlaceIcon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voicerecorderlocation.di.ServiceLocator
import com.example.voicerecorderlocation.ui.components.TrackMap
import com.example.voicerecorderlocation.ui.components.WaveformScrubber
import com.example.voicerecorderlocation.ui.theme.Bg
import com.example.voicerecorderlocation.ui.theme.Coral
import com.example.voicerecorderlocation.ui.theme.Gold
import com.example.voicerecorderlocation.ui.theme.GoldSoft
import com.example.voicerecorderlocation.ui.theme.Hair
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.ui.theme.NumFamily
import com.example.voicerecorderlocation.ui.theme.OnMint
import com.example.voicerecorderlocation.ui.theme.Panel
import com.example.voicerecorderlocation.ui.theme.Panel2
import com.example.voicerecorderlocation.ui.theme.TextDim
import com.example.voicerecorderlocation.ui.theme.TextHi
import com.example.voicerecorderlocation.ui.theme.TextMut
import com.example.voicerecorderlocation.util.compassLabel
import com.example.voicerecorderlocation.util.formatClock
import com.example.voicerecorderlocation.util.locationAtProgress
import com.example.voicerecorderlocation.util.parseWaveform
import com.example.voicerecorderlocation.util.routeDistanceKm
import com.google.maps.android.compose.MapType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class PlayerState(val player: MediaPlayer?, val error: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(sessionId: Long, onBack: () -> Unit) {
    val viewModel: PlaybackViewModel = viewModel(
        factory = PlaybackViewModel.factory(sessionId, ServiceLocator.repository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session
    val points = state.points
    val markers = state.markers

    // Prepare the MediaPlayer off the main thread to avoid ANR on large/slow files.
    val playerState by produceState(PlayerState(null, false), session?.audioPath) {
        val path = session?.audioPath
        if (path == null) {
            value = PlayerState(null, false)
            return@produceState
        }
        val mp = MediaPlayer()
        val ok = withContext(Dispatchers.IO) {
            runCatching { mp.setDataSource(path); mp.prepare() }.isSuccess
        }
        value = if (ok) PlayerState(mp, false) else { runCatching { mp.release() }; PlayerState(null, true) }
        awaitDispose { runCatching { mp.release() } }
    }
    val player = playerState.player
    val duration = (player?.duration ?: session?.durationMillis?.toInt() ?: 1).coerceAtLeast(1)

    var progress by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }

    DisposableEffect(playerState) {
        player?.setOnCompletionListener { progress = 1f; playing = false }
        player?.setOnErrorListener { _, _, _ -> playing = false; true }
        onDispose {
            player?.setOnCompletionListener(null)
            player?.setOnErrorListener(null)
        }
    }
    LaunchedEffect(player, playing) {
        while (playing && player != null) {
            progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            delay(80)
        }
    }

    val progressMillis = (progress * duration).toLong()
    val hasTrack = points.size >= 2
    val current = locationAtProgress(points, session?.startedAtMillis, progressMillis)
    val distKm = routeDistanceKm(points)
    val avgSpeed = if (duration > 0) distKm / (duration / 3_600_000.0) else 0.0
    val elevGain = points.zipWithNext().sumOf { (a, b) ->
        val d = (b.altitudeMeters ?: 0.0) - (a.altitudeMeters ?: 0.0); if (d > 0) d else 0.0
    }

    var editing by remember { mutableStateOf(false) }
    var titleDraft by remember(session?.id) { mutableStateOf(session?.title ?: "") }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // Map area
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (hasTrack) {
                TrackMap(points, session?.startedAtMillis, progressMillis, markers, mapType, Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    PlaceIcon(tint = TextDim, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("该录音没有定位轨迹", color = TextDim, fontSize = 13.sp)
                }
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(14.dp).size(40.dp).clip(CircleShape)
                    .background(Color(0xB30A0C0E)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextHi, modifier = Modifier.size(20.dp)) }
            if (hasTrack) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(100.dp))
                        .background(Color(0xB30A0C0E)).border(1.dp, Hair, RoundedCornerShape(100.dp)).padding(3.dp)
                ) {
                    MapTab("地图", mapType == MapType.NORMAL) { mapType = MapType.NORMAL }
                    MapTab("卫星", mapType == MapType.SATELLITE) { mapType = MapType.SATELLITE }
                }
            }
        }

        // Bottom sheet
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Bg).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + direction
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    OutlinedTextField(value = titleDraft, onValueChange = { titleDraft = it },
                        singleLine = true, modifier = Modifier.weight(1f))
                    Text("完成", color = Mint, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp).clickable { viewModel.rename(titleDraft); editing = false })
                } else {
                    Row(Modifier.weight(1f).clickable { editing = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(session?.title ?: "播放", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Edit, "重命名", tint = TextDim, modifier = Modifier.size(15.dp))
                    }
                    if (hasTrack) Text("朝${compassLabel(current?.bearingDegrees)}", color = Mint, fontFamily = NumFamily, fontSize = 13.sp)
                }
            }

            if (playerState.error) Text("音频文件不可用", color = Coral, fontSize = 13.sp)

            // Stats bar
            if (hasTrack) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Hair, RoundedCornerShape(12.dp))) {
                    PlayStat("里程", "%.2f".format(distKm), "km")
                    StatDivider()
                    PlayStat("均速", "%.1f".format(avgSpeed), "km/h")
                    StatDivider()
                    PlayStat("爬升", "${elevGain.toInt()}", "m")
                    StatDivider()
                    PlayStat("地点", "${markers.size}", null)
                }
            }

            // Place chips
            if (markers.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(markers.size) { i ->
                        val m = markers[i]
                        val active = m.elapsedMillis <= progressMillis
                        Row(
                            Modifier.clip(RoundedCornerShape(100.dp))
                                .background(if (active) GoldSoft else Panel2)
                                .border(1.dp, if (active) Gold.copy(alpha = .4f) else Hair, RoundedCornerShape(100.dp))
                                .clickable {
                                    val ms = m.elapsedMillis.toInt().coerceIn(0, duration)
                                    runCatching { player?.seekTo(ms) }; progress = ms.toFloat() / duration
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Gold.copy(alpha = if (active) 1f else .5f)))
                            Spacer(Modifier.width(7.dp))
                            Text(m.name, color = if (active) Gold else TextMut, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Waveform scrubber
            WaveformScrubber(
                waveform = parseWaveform(session?.waveform),
                progress = progress,
                markerFractions = markers.map { (it.elapsedMillis.toFloat() / duration).coerceIn(0f, 1f) },
                onSeek = { f -> progress = f; runCatching { player?.seekTo((f * duration).toInt()) } }
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(formatClock(progressMillis), color = TextMut, fontFamily = NumFamily, fontSize = 12.sp)
                Text(formatClock(duration.toLong()), color = TextMut, fontFamily = NumFamily, fontSize = 12.sp)
            }

            // Transport
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), Alignment.CenterVertically) {
                CanvasTransportBtn({ FastRewindIcon(tint = TextHi, modifier = it) }) {
                    val ms = ((progress * duration).toInt() - 15000).coerceIn(0, duration)
                    runCatching { player?.seekTo(ms) }; progress = ms.toFloat() / duration
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Mint).clickable {
                        val p = player ?: return@clickable
                        runCatching { if (playing) p.pause() else p.start() }.onSuccess { playing = !playing }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    if (playing) PauseIcon(tint = OnMint, modifier = Modifier.size(28.dp))
                    else Icon(Icons.Filled.PlayArrow, "播放/暂停", tint = OnMint, modifier = Modifier.size(28.dp))
                }
                CanvasTransportBtn({ FastForwardIcon(tint = TextHi, modifier = it) }) {
                    val ms = ((progress * duration).toInt() + 15000).coerceIn(0, duration)
                    runCatching { player?.seekTo(ms) }; progress = ms.toFloat() / duration
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable private fun RowScope.PlayStat(k: String, v: String, unit: String?) {
    Column(Modifier.weight(1f).background(Panel).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v, color = TextHi, fontFamily = NumFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (unit != null) Text(unit, color = TextMut, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
        }
        Text(k, color = TextDim, fontSize = 10.sp)
    }
}

@Composable private fun StatDivider() =
    Box(Modifier.width(1.dp).height(40.dp).background(Hair))

@Composable private fun MapTab(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, color = if (on) OnMint else TextMut, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(100.dp))
            .background(if (on) Mint else Color.Transparent).clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 6.dp))
}

@Composable private fun CanvasTransportBtn(icon: @Composable (Modifier) -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Panel2).border(1.dp, Hair, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { icon(Modifier.size(22.dp)) }
}
