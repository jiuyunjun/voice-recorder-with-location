package com.example.voicerecorderlocation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.util.Locale

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatBearing(bearingDegrees: Float?): String =
    bearingDegrees?.let { "${it.toInt()} deg" } ?: "unknown"

fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    startActivity(intent)
}
