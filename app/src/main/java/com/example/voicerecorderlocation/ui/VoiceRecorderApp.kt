package com.example.voicerecorderlocation.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.voicerecorderlocation.ui.components.ListIcon
import com.example.voicerecorderlocation.ui.components.MicIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.voicerecorderlocation.ui.playback.PlaybackScreen
import com.example.voicerecorderlocation.ui.recording.RecordingScreen
import com.example.voicerecorderlocation.ui.sessions.RecordingListScreen
import com.example.voicerecorderlocation.ui.theme.Bg
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.ui.theme.MintSoft
import com.example.voicerecorderlocation.ui.theme.TextDim

@Composable
fun VoiceRecorderApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showNav = route == "record" || route == "sessions"

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            NavHost(navController, startDestination = "record") {
                composable("record") { RecordingScreen() }
                composable("sessions") {
                    RecordingListScreen(onOpen = { navController.navigate("playback/$it") })
                }
                composable(
                    "playback/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("sessionId") ?: return@composable
                    PlaybackScreen(sessionId = id, onBack = { navController.popBackStack() })
                }
            }
        }
        if (showNav) {
            Row(
                Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NavItem("录音", on = route == "record", modifier = Modifier.weight(1f),
                    icon = { tint -> MicIcon(tint = tint, modifier = Modifier.size(22.dp)) }
                ) {
                    if (route != "record") navController.navigate("record") { popUpTo("record") { inclusive = true } }
                }
                NavItem("录音库", on = route == "sessions", modifier = Modifier.weight(1f),
                    icon = { tint -> ListIcon(tint = tint, modifier = Modifier.size(22.dp)) }
                ) {
                    if (route != "sessions") navController.navigate("sessions")
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    on: Boolean,
    modifier: Modifier,
    icon: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit,
    onClick: () -> Unit
) {
    val tint = if (on) Mint else TextDim
    // Outer box fills the tab slot (clickable area); the inner pill wraps icon + label
    // so the selected-state background hugs the whole item, not just the icon.
    Box(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(18.dp))
                .background(if (on) MintSoft else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon(tint)
            Spacer(Modifier.height(4.dp))
            Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
