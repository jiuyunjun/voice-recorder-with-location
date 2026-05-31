package com.example.voicerecorderlocation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.voicerecorderlocation.data.LocationPointEntity
import com.example.voicerecorderlocation.data.PlaceMarkerEntity
import com.example.voicerecorderlocation.ui.theme.Coral
import com.example.voicerecorderlocation.ui.theme.Gold
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.util.formatBearing
import com.example.voicerecorderlocation.util.locationAtProgress
import com.example.voicerecorderlocation.util.routeSegments
import com.example.voicerecorderlocation.util.smoothRoute
import com.example.voicerecorderlocation.util.traveledSegmentsAtProgress
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * Reusable dark route map: dim full route, Mint traveled route, start dot,
 * heading arrow at the current playback position, and Gold named-place pins.
 */
@Composable
fun TrackMap(
    points: List<LocationPointEntity>,
    sessionStartMillis: Long?,
    progressMillis: Long,
    markers: List<PlaceMarkerEntity> = emptyList(),
    mapType: MapType = MapType.NORMAL,
    modifier: Modifier = Modifier
) {
    val first = points.firstOrNull()
    // Routes are split at GPS gaps so signal-loss stretches aren't bridged by a fake line.
    val routeSegs = routeSegments(points).map { smoothRoute(it) }
    val traveledSegs = traveledSegmentsAtProgress(points, sessionStartMillis, progressMillis).map { smoothRoute(it) }
    val current = locationAtProgress(points, sessionStartMillis, progressMillis)
    val currentLatLng = current?.let { LatLng(it.latitude, it.longitude) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            first?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(35.6812, 139.7671), 15f
        )
    }
    // CameraUpdateFactory is only safe to call after onMapLoaded fires.
    var mapReady by remember { mutableStateOf(false) }

    LaunchedEffect(first?.id, mapReady) {
        if (first != null && mapReady) runCatching {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15f)
            )
        }
    }
    LaunchedEffect(currentLatLng, mapReady) {
        if (currentLatLng != null && mapReady) runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(currentLatLng))
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        onMapLoaded = { mapReady = true },
        properties = MapProperties(
            mapType = mapType,
            mapStyleOptions = if (mapType == MapType.NORMAL) MapStyleOptions(DARK_MAP_STYLE_JSON) else null
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)
    ) {
        routeSegs.forEach { seg ->
            if (seg.size >= 2) Polyline(points = seg, color = Color(0x29FFFFFF), width = 6f)
        }
        traveledSegs.forEach { seg ->
            if (seg.size >= 2) Polyline(points = seg, color = Mint, width = 12f, zIndex = 1f)
        }

        first?.let {
            MarkerComposable(
                state = rememberUpdatedMarkerState(LatLng(it.latitude, it.longitude)),
                anchor = Offset(0.5f, 0.5f), zIndex = 1f, title = "起点"
            ) { StartDot() }
        }

        markers.forEach { m ->
            if (m.latitude != null && m.longitude != null) {
                MarkerComposable(
                    state = rememberUpdatedMarkerState(LatLng(m.latitude, m.longitude)),
                    anchor = Offset(0.5f, 0.5f), zIndex = 2f, title = m.name
                ) { PlaceDot() }
            }
        }

        current?.let {
            val bearing = it.bearingDegrees ?: 0f
            MarkerComposable(
                bearing,
                state = rememberUpdatedMarkerState(LatLng(it.latitude, it.longitude)),
                anchor = Offset(0.5f, 0.5f), flat = true, rotation = bearing,
                title = "当前位置", snippet = "朝 ${formatBearing(it.bearingDegrees)}", zIndex = 3f
            ) { HeadingArrow() }
        }
    }
}

@Composable private fun StartDot() {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(Color(0xFF0D0E10), radius = size.minDimension / 2f)
        drawCircle(Color.White, radius = size.minDimension / 2f, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color.White, radius = size.minDimension / 6f)
    }
}

@Composable private fun PlaceDot() {
    Canvas(modifier = Modifier.size(16.dp)) {
        drawCircle(Gold, radius = size.minDimension / 2f)
        drawCircle(Color(0xFF06120D), radius = size.minDimension / 5f)
    }
}

@Composable private fun HeadingArrow() {
    Canvas(modifier = Modifier.size(34.dp)) {
        drawCircle(Mint.copy(alpha = 0.20f), radius = size.minDimension / 2f)
        val w = size.width; val h = size.height
        val arrow = Path().apply {
            moveTo(w / 2f, h * 0.18f)
            lineTo(w * 0.74f, h * 0.82f)
            lineTo(w / 2f, h * 0.64f)
            lineTo(w * 0.26f, h * 0.82f)
            close()
        }
        drawPath(arrow, Mint)
        drawPath(arrow, Color(0xFF06120D), style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
    }
}
