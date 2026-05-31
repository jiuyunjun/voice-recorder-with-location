package com.example.voicerecorderlocation.ui.recording

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.voicerecorderlocation.ui.components.FlagIcon
import com.example.voicerecorderlocation.ui.components.PlaceIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.voicerecorderlocation.RecordingRuntimeState
import com.example.voicerecorderlocation.di.ServiceLocator
import com.example.voicerecorderlocation.tracking.TrackingForegroundService
import com.example.voicerecorderlocation.ui.components.LiveWaveform
import com.example.voicerecorderlocation.ui.components.StatChip
import com.example.voicerecorderlocation.ui.components.TrackMap
import com.example.voicerecorderlocation.ui.theme.Bg
import com.example.voicerecorderlocation.ui.theme.Coral
import com.example.voicerecorderlocation.ui.theme.CoralSoft
import com.example.voicerecorderlocation.ui.theme.Gold
import com.example.voicerecorderlocation.ui.theme.GoldSoft
import com.example.voicerecorderlocation.ui.theme.Hair
import com.example.voicerecorderlocation.ui.theme.Hair2
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.ui.theme.NumFamily
import com.example.voicerecorderlocation.ui.theme.OnMint
import com.example.voicerecorderlocation.ui.theme.Panel
import com.example.voicerecorderlocation.ui.theme.Panel2
import com.example.voicerecorderlocation.ui.theme.TextDim
import com.example.voicerecorderlocation.ui.theme.TextHi
import com.example.voicerecorderlocation.ui.theme.TextMut
import com.example.voicerecorderlocation.util.formatClock
import com.example.voicerecorderlocation.util.openAppSettings
import com.example.voicerecorderlocation.util.routeDistanceKm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    val rec = RecordingRuntimeState.isRecording
    val startedAt = RecordingRuntimeState.startedAtMillis
    val level = RecordingRuntimeState.amplitudeLevel
    val accuracy = RecordingRuntimeState.locationAccuracyMeters
    val pointCount = RecordingRuntimeState.pointCount
    val markers = RecordingRuntimeState.markers
    val activeId = RecordingRuntimeState.activeSessionId

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rec) { while (rec) { now = System.currentTimeMillis(); delay(100) } }
    val elapsed = startedAt?.let { now - it }?.coerceAtLeast(0L) ?: 0L

    val points by remember(activeId) {
        if (activeId == null) flowOf(emptyList()) else ServiceLocator.repository.observePoints(activeId)
    }.collectAsStateWithLifecycle(emptyList())
    val distKm = routeDistanceKm(points)

    var naming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            startForegroundService(context, TrackingForegroundService.startIntent(context))
        } else {
            permissionDenied = true
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 20.dp)) {
        Text("录音", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 26.sp,
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))

        // Live mini-map
        Box(
            Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0B1512)).border(1.dp, Hair, RoundedCornerShape(18.dp))
        ) {
            if (rec && points.isNotEmpty()) {
                // Pass a fixed sentinel progress (not `elapsed`) so the map only
                // recomposes when new points arrive — not every 100ms with the timer.
                TrackMap(points = points, sessionStartMillis = startedAt,
                    progressMillis = Long.MAX_VALUE / 2, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    if (rec) "正在获取定位信号…" else "开始录音后实时绘制轨迹",
                    color = TextDim, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center)
                )
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp)
                    .background(Color(0x73000000), RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (rec) "记录轨迹中 · $pointCount 点" else "等待定位信号",
                    color = if (rec) Mint else TextMut, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Timer + waveform
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatePill(rec)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(elapsed), color = TextHi, fontFamily = NumFamily,
                    fontWeight = FontWeight.Medium, fontSize = 64.sp)
                Text(".%02d".format((elapsed % 1000) / 10), color = TextDim,
                    fontFamily = NumFamily, fontSize = 28.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            LiveWaveform(active = rec, level = level,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 6.dp))
        }

        // Stats
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("距离", "%.2f".format(distKm), "km", modifier = Modifier.weight(1f))
            StatChip("GPS 精度", accuracy?.let { "±${it.roundToInt()}" } ?: "—", "m", modifier = Modifier.weight(1f))
            StatChip("坐标点", "$pointCount", modifier = Modifier.weight(1f))
        }

        // Marker chips
        if (markers.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(markers.size) { i ->
                    val m = markers[i]
                    Row(
                        Modifier.background(GoldSoft, RoundedCornerShape(100.dp))
                            .border(1.dp, Gold.copy(alpha = .3f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.name, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(7.dp))
                        Text(formatClock(m.elapsedMillis), color = TextDim, fontFamily = NumFamily, fontSize = 11.sp)
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    .background(Panel, RoundedCornerShape(12.dp))
                    .border(1.dp, Hair, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text(
                    if (rec) "点击左侧旗标按钮标记当前地点" else "后台定位已授权 · 锁屏后继续记录轨迹",
                    color = TextMut, fontSize = 12.sp
                )
            }
        }

        // Record controls
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(Panel2).border(1.dp, Hair, CircleShape)
                    .clickable(enabled = rec) { naming = true; draft = "" },
                contentAlignment = Alignment.Center
            ) {
                FlagIcon(tint = if (rec) Mint else TextDim, modifier = Modifier.size(20.dp))
            }
            RecordButton(rec) {
                if (rec) context.startService(TrackingForegroundService.stopIntent(context))
                else permissionLauncher.launch(requiredRuntimePermissions())
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Text("${markers.size}", color = if (markers.isNotEmpty()) Mint else TextDim,
                    fontFamily = NumFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("地点", color = TextMut, fontSize = 11.sp)
            }
        }
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("需要权限") },
            text = { Text("录音需要麦克风和定位权限。请在系统设置中开启后重试。") },
            confirmButton = {
                TextButton(onClick = { permissionDenied = false; context.openAppSettings() }) {
                    Text("去设置", color = Mint)
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionDenied = false }) { Text("取消") }
            }
        )
    }

    if (naming) {
        ModalBottomSheet(onDismissRequest = { naming = false }, sheetState = sheetState, containerColor = Panel) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaceIcon(tint = Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("标记当前地点", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Text("将在 ${formatClock(elapsed)} 处插入一个命名标记", color = TextMut, fontSize = 12.sp)
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    placeholder = { Text("给这个地点起个名字…", color = TextDim) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quick = listOf("出发点", "休息点", "折返点", "风景好", "重要内容")
                    items(quick.size) { i ->
                        Text(quick[i], color = TextMut, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(100.dp))
                                .background(Panel2).border(1.dp, Hair, RoundedCornerShape(100.dp))
                                .clickable { draft = quick[i] }
                                .padding(horizontal = 13.dp, vertical = 8.dp))
                    }
                }
                Button(
                    onClick = {
                        context.startService(TrackingForegroundService.markIntent(context, draft.ifBlank { "标记 ${markers.size + 1}" }))
                        naming = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = OnMint),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存标记", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun StatePill(rec: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(100.dp))
            .background(if (rec) CoralSoft else Panel2)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (rec) Coral else TextMut))
        Spacer(Modifier.width(8.dp))
        Text(if (rec) "录音中" else "准备就绪", color = if (rec) Coral else TextMut,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun RecordButton(rec: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(84.dp).clip(CircleShape).border(4.dp, Hair2, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(if (rec) 30.dp else 60.dp)
                .clip(RoundedCornerShape(if (rec) 9.dp else 30.dp))
                .background(Coral)
        )
    }
}

private fun requiredRuntimePermissions(): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()
