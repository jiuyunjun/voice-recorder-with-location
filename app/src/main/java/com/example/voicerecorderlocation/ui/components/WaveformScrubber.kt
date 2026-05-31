package com.example.voicerecorderlocation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.voicerecorderlocation.ui.theme.Gold
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.ui.theme.Panel3

/**
 * 波形回放 — static waveform that doubles as the seek scrubber.
 * Played portion is Mint, unplayed is dim, gold dots mark named places.
 */
@Composable
fun WaveformScrubber(
    waveform: List<Float>,
    progress: Float,
    markerFractions: List<Float>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bars = waveform.ifEmpty { List(120) { 0.18f } }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(Unit) {
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val n = bars.size
        val gap = 2.dp.toPx()
        val barW = ((size.width - (n - 1) * gap) / n).coerceAtLeast(1f)
        val cy = size.height / 2f
        var x = 0f
        for (i in 0 until n) {
            val frac = (i + 0.5f) / n
            val h = (12f + bars[i].coerceIn(0f, 1f) * (size.height * 0.76f))
            val color = if (frac <= progress) Mint else Panel3
            drawRoundRect(
                color = color,
                topLeft = Offset(x, cy - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW.coerceAtMost(2.dp.toPx()))
            )
            x += barW + gap
        }
        markerFractions.forEach { f ->
            drawCircle(Gold, radius = 3.5.dp.toPx(), center = Offset(size.width * f, 4.dp.toPx()))
        }
        val px = size.width * progress.coerceIn(0f, 1f)
        drawRect(Color.White, topLeft = Offset(px - 1f, 0f), size = Size(2f, size.height))
        drawCircle(Color.White, radius = 4.5.dp.toPx(), center = Offset(px, 4.dp.toPx()))
    }
}
