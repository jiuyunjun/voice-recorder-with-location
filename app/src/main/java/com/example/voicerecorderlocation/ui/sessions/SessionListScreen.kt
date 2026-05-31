package com.example.voicerecorderlocation.ui.sessions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import com.example.voicerecorderlocation.ui.components.FileUploadIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voicerecorderlocation.data.RecordingSessionEntity
import com.example.voicerecorderlocation.di.ServiceLocator
import com.example.voicerecorderlocation.ui.components.TrackThumbnail
import com.example.voicerecorderlocation.ui.theme.Bg
import com.example.voicerecorderlocation.ui.theme.Coral
import com.example.voicerecorderlocation.ui.theme.CoralSoft
import com.example.voicerecorderlocation.ui.theme.Hair
import com.example.voicerecorderlocation.ui.theme.Hair2
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.ui.theme.MintSoft
import com.example.voicerecorderlocation.ui.theme.NumFamily
import com.example.voicerecorderlocation.ui.theme.OnMint
import com.example.voicerecorderlocation.ui.theme.Panel
import com.example.voicerecorderlocation.ui.theme.Panel2
import com.example.voicerecorderlocation.ui.theme.Panel3
import com.example.voicerecorderlocation.ui.theme.TextDim
import com.example.voicerecorderlocation.ui.theme.TextHi
import com.example.voicerecorderlocation.ui.theme.TextMut
import com.example.voicerecorderlocation.util.dayLabel
import com.example.voicerecorderlocation.util.formatClock
import com.example.voicerecorderlocation.util.routeDistanceKm
import com.example.voicerecorderlocation.util.timeOfDay
import kotlinx.coroutines.flow.flowOf

@Composable
fun RecordingListScreen(onOpen: (Long) -> Unit, viewModel: RecordingListViewModel = viewModel()) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importZipArchives(uris) }
    val selectedSessions = sessions.filter { it.id in selected }

    if (importError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportError() },
            title = { Text("导入失败") }, text = { Text(importError!!) },
            confirmButton = { TextButton(onClick = { viewModel.clearImportError() }) { Text("确定") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除录音") },
            text = { Text("将删除 ${selected.size} 段录音及其音频、轨迹和地点标记，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSessions(selectedSessions)
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("删除", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("录音库", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                        Text("${sessions.size} 段录音", color = TextMut, fontSize = 13.sp)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Panel2).border(1.dp, Hair, CircleShape)
                            .clickable { importLauncher.launch(arrayOf("application/zip")) },
                        contentAlignment = Alignment.Center
                    ) { FileUploadIcon(tint = TextHi, modifier = Modifier.size(19.dp)) }
                }
            }

            if (sessions.isEmpty()) {
                item { EmptyState() }
            }

            var lastDay: String? = null
            sessions.forEach { session ->
                val label = dayLabel(session.startedAtMillis)
                if (label != lastDay) {
                    lastDay = label
                    item(key = "day-$label") {
                        Text(label, color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                    }
                }
                item(key = session.id) {
                    SessionCard(
                        session = session,
                        selected = session.id in selected,
                        selecting = selecting,
                        onClick = { if (selecting) selected = selected.toggle(session.id) else onOpen(session.id) },
                        onLong = { selected = selected.toggle(session.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(if (selecting) 80.dp else 8.dp)) }
        }

        if (selecting) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Panel3).border(1.dp, Hair2, RoundedCornerShape(18.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionBtn("取消 ${selected.size}", Icons.Filled.Close, Modifier.weight(1f)) { selected = emptySet() }
                ActionBtn("分享", Icons.Filled.Share, Modifier.weight(1f)) {
                    viewModel.exportAndShare(context, selectedSessions)
                }
                ActionBtn("删除", Icons.Filled.Delete, Modifier.weight(1f), danger = true) {
                    confirmDelete = true
                }
            }
        }
    }
}

private fun Set<Long>.toggle(id: Long) = if (contains(id)) this - id else this + id

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Panel2).border(1.dp, Hair, CircleShape),
            contentAlignment = Alignment.Center
        ) { TrackThumbnail(emptyList(), Modifier.size(40.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("还没有录音", color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "切到「录音」页，点下方按钮开始记录\n录音时会同步记录你的位置轨迹",
            color = TextMut, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: RecordingSessionEntity, selected: Boolean, selecting: Boolean,
    onClick: () -> Unit, onLong: () -> Unit
) {
    val points by remember(session.id) {
        ServiceLocator.repository.observePoints(session.id)
    }.collectAsStateWithLifecycle(emptyList())
    val distKm = routeDistanceKm(points)

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) MintSoft else Panel)
            .border(1.dp, if (selected) Mint else Hair, RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1512))
                .border(1.dp, Hair, RoundedCornerShape(14.dp))
        ) { TrackThumbnail(points, Modifier.fillMaxSize()) }

        Column(Modifier.weight(1f)) {
            Text(session.title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(timeOfDay(session.startedAtMillis), color = TextMut, fontFamily = NumFamily, fontSize = 12.sp)
                Dot()
                Text(formatClock(session.durationMillis), color = TextMut, fontFamily = NumFamily, fontSize = 12.sp)
                Dot()
                Text(if (points.size >= 2) "%.2f km".format(distKm) else "无轨迹", color = TextMut, fontSize = 12.sp)
            }
        }

        if (selecting) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (selected) Mint else Color.Transparent)
                    .border(2.dp, if (selected) Mint else Hair2, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (selected) Icon(Icons.Filled.Check, null, tint = OnMint, modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable private fun Dot() =
    Box(Modifier.size(3.dp).clip(CircleShape).background(TextDim))

@Composable
private fun ActionBtn(
    label: String, icon: ImageVector, modifier: Modifier,
    danger: Boolean = false, onClick: () -> Unit
) {
    Row(
        modifier.clip(RoundedCornerShape(100.dp))
            .background(if (danger) CoralSoft else Panel2)
            .border(1.dp, if (danger) Color.Transparent else Hair, RoundedCornerShape(100.dp))
            .clickable { onClick() }.padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) Coral else TextHi, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (danger) Coral else TextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
