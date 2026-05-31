package com.example.voicerecorderlocation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

// ── Canvas replacements for the 4 icons that live only in material-icons-extended ──
// Avoids the ~9 MB extended library that causes heap OOM during compilation.

/** Flag / bookmark icon (替换 Icons.Filled.Flag) */
@Composable
fun FlagIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.06f)
            lineTo(w * 0.78f, h * 0.06f)
            lineTo(w * 0.60f, h * 0.44f)
            lineTo(w * 0.78f, h * 0.82f)
            lineTo(w * 0.22f, h * 0.82f)
            close()
        }
        drawPath(path, tint)
        // flagpole
        drawLine(tint, Offset(w * 0.22f, h * 0.06f), Offset(w * 0.22f, h * 0.96f),
            strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    }
}

/** Upload arrow icon (替换 Icons.Filled.FileUpload) */
@Composable
fun FileUploadIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        // arrow up
        val arrow = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.22f, h * 0.46f)
            lineTo(w * 0.38f, h * 0.46f)
            lineTo(w * 0.38f, h * 0.70f)
            lineTo(w * 0.62f, h * 0.70f)
            lineTo(w * 0.62f, h * 0.46f)
            lineTo(w * 0.78f, h * 0.46f)
            close()
        }
        drawPath(arrow, tint)
        // base line
        drawRect(tint, topLeft = Offset(w * 0.16f, h * 0.80f), size = Size(w * 0.68f, h * 0.10f))
    }
}

/** Fast-forward (替换 Icons.Filled.FastForward) */
@Composable
fun FastForwardIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val tri1 = Path().apply {
            moveTo(w * 0.08f, h * 0.18f); lineTo(w * 0.50f, h * 0.50f); lineTo(w * 0.08f, h * 0.82f); close()
        }
        val tri2 = Path().apply {
            moveTo(w * 0.50f, h * 0.18f); lineTo(w * 0.90f, h * 0.50f); lineTo(w * 0.50f, h * 0.82f); close()
        }
        drawPath(tri1, tint); drawPath(tri2, tint)
    }
}

/** Fast-rewind (替换 Icons.Filled.FastRewind) */
@Composable
fun FastRewindIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val tri1 = Path().apply {
            moveTo(w * 0.92f, h * 0.18f); lineTo(w * 0.50f, h * 0.50f); lineTo(w * 0.92f, h * 0.82f); close()
        }
        val tri2 = Path().apply {
            moveTo(w * 0.50f, h * 0.18f); lineTo(w * 0.10f, h * 0.50f); lineTo(w * 0.50f, h * 0.82f); close()
        }
        drawPath(tri1, tint); drawPath(tri2, tint)
    }
}

/** Location pin (替换 Icons.Filled.Place — extended only) */
@Composable
fun PlaceIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val r = w * 0.34f
        // circle head
        drawCircle(tint, radius = r, center = Offset(cx, r + h * 0.04f))
        // teardrop tail
        val tail = Path().apply {
            moveTo(cx - r * 0.6f, r + h * 0.04f + r * 0.7f)
            lineTo(cx, h * 0.94f)
            lineTo(cx + r * 0.6f, r + h * 0.04f + r * 0.7f)
        }
        drawPath(tail, tint)
        // inner dot
        drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.38f, center = Offset(cx, r + h * 0.04f))
    }
}

/** Microphone (替换 Icons.Filled.Mic — extended only) */
@Composable
fun MicIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f
        val sw = w * 0.085f

        // Capsule body (filled)
        val bw = w * 0.36f; val bh = h * 0.46f; val btop = h * 0.06f
        drawRoundRect(tint,
            topLeft = Offset(cx - bw / 2f, btop),
            size = Size(bw, bh),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2f))

        // Horseshoe arc — endpoints sit just outside the capsule sides
        val arcCy = btop + bh
        val arcR = bw / 2f + w * 0.07f
        drawArc(tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx - arcR, arcCy - arcR),
            size = Size(arcR * 2f, arcR * 2f),
            style = Stroke(width = sw))

        // Stem from arc bottom down to base line
        val stemTop = arcCy + arcR
        val stemBot = h * 0.90f
        drawLine(tint, Offset(cx, stemTop), Offset(cx, stemBot), sw)

        // Base
        drawLine(tint, Offset(cx - bw * 0.65f, stemBot), Offset(cx + bw * 0.65f, stemBot),
            strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Pause bars (替换 Icons.Filled.Pause — extended only) */
@Composable
fun PauseIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val barW = w * 0.22f; val top = h * 0.16f; val bot = h * 0.84f
        drawRoundRect(tint, topLeft = Offset(w * 0.20f, top), size = Size(barW, bot - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3f))
        drawRoundRect(tint, topLeft = Offset(w * 0.58f, top), size = Size(barW, bot - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3f))
    }
}

/** Three horizontal lines (替换 Icons.AutoMirrored.Filled.List — extended only) */
@Composable
fun ListIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val sw = h * 0.085f
        val left = w * 0.10f; val right = w * 0.90f
        listOf(0.22f, 0.50f, 0.78f).forEach { y ->
            drawLine(tint, Offset(left, h * y), Offset(right, h * y),
                strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}
