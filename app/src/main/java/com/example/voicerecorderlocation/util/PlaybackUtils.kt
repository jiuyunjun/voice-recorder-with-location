package com.example.voicerecorderlocation.util

import com.example.voicerecorderlocation.data.LocationPointEntity
import com.google.android.gms.maps.model.LatLng

data class PlaybackLocation(
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float?
)

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

    val fraction = (targetTime - previous.recordedAtMillis).toDouble() / spanMillis.toDouble()
    return PlaybackLocation(
        latitude = previous.latitude + (next.latitude - previous.latitude) * fraction,
        longitude = previous.longitude + (next.longitude - previous.longitude) * fraction,
        bearingDegrees = interpolateBearing(previous.bearingDegrees, next.bearingDegrees, fraction)
    )
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

fun traveledRouteAtProgress(
    points: List<LocationPointEntity>,
    sessionStartMillis: Long?,
    progressMillis: Long
): List<LatLng> {
    if (points.isEmpty()) return emptyList()
    val targetTime = targetTimeAtProgress(points, sessionStartMillis, progressMillis)
    val current = locationAtProgress(points, sessionStartMillis, progressMillis)
    return buildList {
        points
            .filter { it.recordedAtMillis <= targetTime }
            .forEach { add(LatLng(it.latitude, it.longitude)) }
        current?.let { add(LatLng(it.latitude, it.longitude)) }
    }.distinctConsecutive()
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
    bearingDegrees = bearingDegrees
)

private fun List<LatLng>.distinctConsecutive(): List<LatLng> =
    filterIndexed { index, latLng -> index == 0 || latLng != this[index - 1] }
