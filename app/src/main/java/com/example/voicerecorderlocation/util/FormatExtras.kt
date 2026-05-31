package com.example.voicerecorderlocation.util

import java.util.Locale

/** mm:ss, or h:mm:ss past an hour. */
fun formatClock(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/** Chinese 8-point compass label from a bearing in degrees. */
fun compassLabel(bearingDegrees: Float?): String {
    if (bearingDegrees == null) return "—"
    val dirs = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
    return dirs[(Math.round(((bearingDegrees % 360 + 360) % 360) / 45f)) % 8]
}

/** Parse stored waveform CSV ("12,40,…") into 0..1 floats. */
fun parseWaveform(csv: String?): List<Float> =
    csv?.split(',')
        ?.mapNotNull { it.trim().toFloatOrNull() }
        ?.map { (it / 100f).coerceIn(0f, 1f) }
        ?: emptyList()

/** Cumulative great-circle distance of a track in kilometres. */
fun routeDistanceKm(points: List<com.example.voicerecorderlocation.data.LocationPointEntity>): Double {
    if (points.size < 2) return 0.0
    var meters = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude); val la2 = Math.toRadians(b.latitude)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
        meters += 2 * 6_371_000.0 * Math.asin(Math.min(1.0, Math.sqrt(h)))
    }
    return meters / 1000.0
}

/** "今天 / 昨天 / M月d日" grouping label. */
fun dayLabel(startedAtMillis: Long): String {
    val today = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayMs = 86_400_000L
    return when {
        startedAtMillis >= today -> "今天"
        startedAtMillis >= today - dayMs -> "昨天"
        else -> java.util.Calendar.getInstance().run {
            timeInMillis = startedAtMillis
            "${get(java.util.Calendar.MONTH) + 1}月${get(java.util.Calendar.DAY_OF_MONTH)}日"
        }
    }
}

/** HH:mm clock time from epoch millis. */
fun timeOfDay(millis: Long): String =
    java.util.Calendar.getInstance().run {
        timeInMillis = millis
        String.format(Locale.US, "%02d:%02d", get(java.util.Calendar.HOUR_OF_DAY), get(java.util.Calendar.MINUTE))
    }
