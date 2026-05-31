package com.example.voicerecorderlocation.util

import com.example.voicerecorderlocation.data.LocationPointEntity
import com.google.android.gms.maps.model.LatLng

data class PlaybackLocation(
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float?,
    val speedMps: Float?
)

// Points normally arrive every ~2 s. A larger gap means GPS signal loss — the path
// between the two points is unknown and must not be interpolated/bridged.
private const val GAP_THRESHOLD_MILLIS = 15_000L

fun locationAtProgress(
    points: List<LocationPointEntity>,
    sessionStartMillis: Long?,
    progressMillis: Long
): PlaybackLocation? {
    if (points.isEmpty()) return null
    val targetTime = targetTimeAtProgress(points, sessionStartMillis, progressMillis)
    val nextIndex = points.indexOfFirst { it.recordedAtMillis >= targetTime }

    // -1 means all points are before target time — show the last known position
    if (nextIndex == -1) return points.last().toPlaybackLocation()
    // 0 means we haven't reached any point yet — show the first position
    if (nextIndex == 0) return points.first().toPlaybackLocation()

    val previous = points[nextIndex - 1]
    val next = points[nextIndex]
    val spanMillis = next.recordedAtMillis - previous.recordedAtMillis
    if (spanMillis <= 0L) return next.toPlaybackLocation()

    // GPS gap (signal loss): don't fabricate a straight line across it — the real path
    // is unknown. Hold at the last known point until the track resumes.
    if (spanMillis > GAP_THRESHOLD_MILLIS) return previous.toPlaybackLocation()

    val fraction = (targetTime - previous.recordedAtMillis).toDouble() / spanMillis.toDouble()
    return PlaybackLocation(
        latitude = previous.latitude + (next.latitude - previous.latitude) * fraction,
        longitude = previous.longitude + (next.longitude - previous.longitude) * fraction,
        bearingDegrees = interpolateBearing(previous.bearingDegrees, next.bearingDegrees, fraction),
        speedMps = interpolateSpeed(previous.speedMetersPerSecond, next.speedMetersPerSecond, fraction)
    )
}

/** Linear interpolation between two speeds, tolerating nulls. */
private fun interpolateSpeed(a: Float?, b: Float?, fraction: Double): Float? {
    if (a == null) return b
    if (b == null) return a
    return (a + (b - a) * fraction).toFloat()
}

/** Shortest-arc interpolation between two compass bearings, handling the 359°→1° wrap. */
private fun interpolateBearing(a: Float?, b: Float?, fraction: Double): Float? {
    if (a == null) return b
    if (b == null) return a
    var diff = (b - a).toDouble()
    while (diff > 180.0) diff -= 360.0
    while (diff < -180.0) diff += 360.0
    val result = a + diff * fraction
    return (((result % 360.0) + 360.0) % 360.0).toFloat()
}

/**
 * The full route split into continuous segments at GPS gaps, so the map can draw each
 * segment as a separate polyline instead of bridging signal-loss gaps with a fake line.
 */
fun routeSegments(points: List<LocationPointEntity>): List<List<LatLng>> =
    points.segmentedByGaps().map { seg -> seg.map { LatLng(it.latitude, it.longitude) } }

/**
 * The traveled-so-far route (up to [progressMillis]), split into gap-free segments, with
 * the interpolated current position appended to the final segment.
 */
fun traveledSegmentsAtProgress(
    points: List<LocationPointEntity>,
    sessionStartMillis: Long?,
    progressMillis: Long
): List<List<LatLng>> {
    if (points.isEmpty()) return emptyList()
    val targetTime = targetTimeAtProgress(points, sessionStartMillis, progressMillis)
    val passed = points.filter { it.recordedAtMillis <= targetTime }
    if (passed.isEmpty()) return emptyList()

    val segments = passed.segmentedByGaps()
        .map { seg -> seg.map { LatLng(it.latitude, it.longitude) }.toMutableList() }
    val current = locationAtProgress(points, sessionStartMillis, progressMillis)
    if (current != null && segments.isNotEmpty()) {
        segments.last().add(LatLng(current.latitude, current.longitude))
    }
    return segments.map { it.distinctConsecutive() }
}

/** Split a time-ordered point list wherever the inter-point gap exceeds the threshold. */
private fun List<LocationPointEntity>.segmentedByGaps(): List<List<LocationPointEntity>> {
    if (isEmpty()) return emptyList()
    val out = mutableListOf<MutableList<LocationPointEntity>>()
    var current = mutableListOf(this[0])
    for (i in 1 until size) {
        if (this[i].recordedAtMillis - this[i - 1].recordedAtMillis > GAP_THRESHOLD_MILLIS) {
            out.add(current)
            current = mutableListOf()
        }
        current.add(this[i])
    }
    out.add(current)
    return out
}

fun targetTimeAtProgress(
    points: List<LocationPointEntity>,
    sessionStartMillis: Long?,
    progressMillis: Long
): Long = (sessionStartMillis ?: points.firstOrNull()?.recordedAtMillis ?: 0L) + progressMillis

fun smoothRoute(route: List<LatLng>): List<LatLng> {
    if (route.size < 3) return route
    return route.mapIndexed { index, point ->
        if (index == 0 || index == route.lastIndex) {
            point
        } else {
            val previous = route[index - 1]
            val next = route[index + 1]
            LatLng(
                previous.latitude * 0.25 + point.latitude * 0.5 + next.latitude * 0.25,
                previous.longitude * 0.25 + point.longitude * 0.5 + next.longitude * 0.25
            )
        }
    }
}

private fun LocationPointEntity.toPlaybackLocation() = PlaybackLocation(
    latitude = latitude,
    longitude = longitude,
    bearingDegrees = bearingDegrees,
    speedMps = speedMetersPerSecond
)

private fun List<LatLng>.distinctConsecutive(): List<LatLng> =
    filterIndexed { index, latLng -> index == 0 || latLng != this[index - 1] }
