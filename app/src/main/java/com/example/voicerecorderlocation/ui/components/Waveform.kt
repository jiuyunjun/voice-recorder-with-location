package com.example.voicerecorderlocation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.voicerecorderlocation.ui.theme.Mint
import com.example.voicerecorderlocation.ui.theme.TextDim
import kotlin.math.abs
import kotlin.math.sin

/**
 * Live recording waveform — animated bars that respond to [level] (0..1).
 */
@Composable
fun LiveWaveform(
    active: Boolean,
    level: Float,
    modifier: Modifier = Modifier,
    bars: Int = 40
) {
    val seeds = remember(bars) { FloatArray(bars) { abs(sin(it * 1.7f)) * 0.5f + 0.2f } }
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val color = if (active) Mint else TextDim
    Canvas(modifier = modifier) {
        val n = seeds.size
        val gap = 3.dp.toPx()
        val barW = 3.dp.toPx()
        val totalW = n * barW + (n - 1) * gap
        var x = (size.width - totalW) / 2f
        val cy = size.height / 2f
        val maxH = size.height
        for (i in 0 until n) {
            val s = seeds[i]
            val h = if (active)
                (6f + (s * 0.4f + level * 0.9f) * (maxH * 0.9f) * (0.6f + 0.4f * sin(phase + i))).coerceIn(4f, maxH)
            else (4f + s * 10f)
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.9f else 0.5f),
                topLeft = Offset(x, cy - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
            x += barW + gap
        }
    }
}
