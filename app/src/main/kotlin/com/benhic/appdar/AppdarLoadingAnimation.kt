package com.benhic.appdar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated radar / app-grid loading indicator matching the Appdar brand icon motif.
 *
 * Faithfully ports the SVG animation from appdar_loading.html:
 *  - Rotating sweep arm with 90° trailing sector
 *  - Three pulsing concentric rings
 *  - Three staggered amber blips
 *  - Pulsing centre dot with glow halo
 *  - Six pulsing app dots in a 3×2 grid
 *
 * All positions use the SVG's 120×120 coordinate space and scale to whatever
 * size [modifier] resolves to at runtime.
 */
@Composable
fun AppdarRadarAnimation(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "radar")

    // ── Sweep arm – full rotation every 2 800 ms ───────────────────────────
    val sweepDeg by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    // ── Concentric rings – slight period variation gives natural phase drift ─
    val ring1A by t.animateFloat(0.20f, 0.45f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "r1")
    val ring2A by t.animateFloat(0.25f, 0.42f,
        infiniteRepeatable(tween(1540, easing = FastOutSlowInEasing), RepeatMode.Reverse), "r2")
    val ring3A by t.animateFloat(0.30f, 0.40f,
        infiniteRepeatable(tween(1260, easing = FastOutSlowInEasing), RepeatMode.Reverse), "r3")

    // ── Blips – keyframe opacity curves over the 2 800 ms sweep cycle ─────
    // blip1: 0%→0  20%→0.95  60%→0.6  80%→0
    val blip1A by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 2800
        0f    at 0;  0.95f at 560;  0.6f at 1680;  0f at 2240;  0f at 2800
    }, RepeatMode.Restart), "b1")

    // blip2: 0%→0  35%→0.7  70%→0.3  90%→0
    val blip2A by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 2800
        0f at 0;  0.7f at 980;  0.3f at 1960;  0f at 2520;  0f at 2800
    }, RepeatMode.Restart), "b2")

    // blip3: 0%→0  50%→0.45  80%→0
    val blip3A by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 2800
        0f at 0;  0.45f at 1400;  0f at 2240;  0f at 2800
    }, RepeatMode.Restart), "b3")

    // ── Centre dot & glow ring – pulse together at 700 ms half-period ──────
    val centerR by t.animateFloat(3.5f, 4.5f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "cr")
    val glowR   by t.animateFloat(18f,  22f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "gr")
    val glowA   by t.animateFloat(0.1f, 0.2f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "ga")

    // ── App dots – staggered periods so neighbours are out of phase ────────
    val dot1A by t.animateFloat(0.85f, 0.50f,
        infiniteRepeatable(tween(900,  easing = FastOutSlowInEasing), RepeatMode.Reverse), "d1")
    val dot2A by t.animateFloat(0.85f, 0.50f,
        infiniteRepeatable(tween(1050, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d2")
    val dot3A by t.animateFloat(0.85f, 0.50f,
        infiniteRepeatable(tween(780,  easing = FastOutSlowInEasing), RepeatMode.Reverse), "d3")

    Canvas(modifier = modifier) {
        val s  = size.width / 120f   // scale: SVG 120×120 → canvas coords
        val cx = size.width  / 2f
        val cy = size.height / 2f

        // ── 1. Background rounded rect with radial gradient ────────────────
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF6259C4), Color(0xFF3C3489)),
                center = Offset(size.width * 0.4f, size.height * 0.35f),
                radius = size.width * 0.65f
            ),
            cornerRadius = CornerRadius(24f * s)
        )

        // ── 2. Three concentric rings ──────────────────────────────────────
        for ((r, a) in listOf(46f to ring1A, 32f to ring2A, 18f to ring3A)) {
            drawCircle(Color.White.copy(alpha = a), radius = r * s,
                center = Offset(cx, cy), style = Stroke(0.8f * s))
        }

        // ── 3. Sweep sector + arm ──────────────────────────────────────────
        // Compose angle convention: 0° = 3 o'clock, clockwise.
        // Arm starts at 12 o'clock (−90°) and rotates with sweepDeg.
        val armAngle = -90f + sweepDeg
        val outerR   = 46f * s

        // Translucent sector that trails 90° behind the arm
        drawArc(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x30534AB7), Color.Transparent),
                center = Offset(cx, cy), radius = outerR
            ),
            startAngle = armAngle, sweepAngle = 90f, useCenter = true,
            topLeft = Offset(cx - outerR, cy - outerR),
            size    = Size(outerR * 2f, outerR * 2f)
        )

        // Arm line
        val rad = Math.toRadians(armAngle.toDouble())
        drawLine(
            Color.White.copy(alpha = 0.65f),
            start = Offset(cx, cy),
            end   = Offset((cx + outerR * cos(rad)).toFloat(),
                           (cy + outerR * sin(rad)).toFloat()),
            strokeWidth = 1.5f * s, cap = StrokeCap.Round
        )

        // ── 4. Amber blips ─────────────────────────────────────────────────
        // SVG positions in 120×120 space: (x, y, radius)
        val amber = Color(0xFFFAC775)
        data class Blip(val x: Float, val y: Float, val r: Float, val alpha: Float)
        listOf(
            Blip(82f, 38f, 3.5f, blip1A),
            Blip(72f, 52f, 2.5f, blip2A),
            Blip(91f, 55f, 2.0f, blip3A)
        ).forEach { b ->
            if (b.alpha > 0.01f)
                drawCircle(amber.copy(alpha = b.alpha), radius = b.r * s,
                    center = Offset(b.x * s, b.y * s))
        }

        // ── 5. Centre glow halo + white dot ───────────────────────────────
        drawCircle(Color(0xFF534AB7).copy(alpha = glowA),
            radius = glowR * s, center = Offset(cx, cy))
        drawCircle(Color.White,
            radius = centerR * s, center = Offset(cx, cy))

        // ── 6. App-icon dot grid (3 columns × 2 rows) ─────────────────────
        // SVG: row 1 y=86, row 2 y=100; x = 42, 60, 78; radius = 5.
        // Dots 4 and 6 (corners of row 2) are "dim" — matches dot-pulse-dim.
        data class Dot(val x: Float, val y: Float, val alpha: Float, val dim: Boolean)
        val dotR = 5f * s
        listOf(
            Dot(42f,  86f, dot1A, false),
            Dot(60f,  86f, dot2A, false),
            Dot(78f,  86f, dot3A, false),
            Dot(42f, 100f, dot2A, true),
            Dot(60f, 100f, dot1A, false),
            Dot(78f, 100f, dot3A, true)
        ).forEach { d ->
            val a = if (d.dim) d.alpha * 0.5f else d.alpha
            drawCircle(Color.White.copy(alpha = a), radius = dotR,
                center = Offset(d.x * s, d.y * s))
        }
    }
}
