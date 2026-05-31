package com.example.voicerecorderlocation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import com.example.voicerecorderlocation.ui.theme.SoundTrailTheme
import androidx.compose.ui.Modifier
import com.example.voicerecorderlocation.ui.VoiceRecorderApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoundTrailTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceRecorderApp()
                }
            }
        }
    }
}
