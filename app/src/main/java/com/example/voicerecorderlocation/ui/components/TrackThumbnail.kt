package com.example.voicerecorderlocation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.voicerecorderlocation.data.LocationPointEntity
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.ui.theme.TextDim

/**
 * Cheap list-friendly track preview drawn with Canvas (no GoogleMap per row).
 * Normalises lat/lng to the bounding box; single/no-point shows a centred dot.
 */
@Composable
fun TrackThumbnail(points: List<LocationPointEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pad = size.minDimension * 0.18f
        if (points.size < 2) {
            drawCircle(TextDim, radius = size.minDimension * 0.06f,
                center = Offset(size.width / 2f, size.height / 2f))
            return@Canvas
        }
        val lats = points.map { it.latitude }
        val lngs = points.map { it.longitude }
        val minLat = lats.min(); val maxLat = lats.max()
        val minLng = lngs.min(); val maxLng = lngs.max()
        val spanLat = (maxLat - minLat).takeIf { it > 1e-9 } ?: 1.0
        val spanLng = (maxLng - minLng).takeIf { it > 1e-9 } ?: 1.0
        val w = size.width - pad * 2; val h = size.height - pad * 2
        fun px(p: LocationPointEntity) = Offset(
            pad + ((p.longitude - minLng) / spanLng).toFloat() * w,
            pad + (1f - ((p.latitude - minLat) / spanLat).toFloat()) * h
        )
        val path = Path().apply {
            points.forEachIndexed { i, p ->
                val o = px(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        drawPath(path, Mint, style = Stroke(
            width = size.minDimension * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round
        ))
    }
}
