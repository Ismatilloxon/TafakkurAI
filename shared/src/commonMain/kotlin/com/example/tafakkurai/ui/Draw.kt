package com.example.tafakkurai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 0.5px bottom divider (borderBottom: 0.5px solid sep). */
fun Modifier.drawBottomHairline(color: Color): Modifier = drawBehind {
    val y = size.height
    drawLine(color, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
}

fun Modifier.drawTopHairline(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx())
}

/** Soft radial glow behind hero content (AmbientGlow). */
@Composable
fun AmbientGlow(color: Color, modifier: Modifier = Modifier, opacity: Float = 0.5f) {
    Box(
        modifier.size(width = 420.dp, height = 300.dp).drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = opacity), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    )
}

/** Breathing orb — gentle pulsing concentric rings (companion + reflection). */
@Composable
fun BreathOrb(size: Int = 120, color: Color, purple: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
    )
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.scale(pulse).size(size.dp).drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.06f))),
                    radius = this.size.minDimension / 2f,
                )
            }
        )
        Box(
            Modifier.scale(pulse).size((size * 0.64f).dp).drawBehind {
                drawCircle(color.copy(alpha = 0.5f), radius = this.size.minDimension / 2f, style = Stroke(1.dp.toPx()))
            }
        )
        Box(
            Modifier.scale(pulse).size((size * 0.36f).dp).drawBehind {
                drawCircle(Brush.linearGradient(listOf(color, purple)), radius = this.size.minDimension / 2f)
            },
            contentAlignment = Alignment.Center,
        ) { TfIcon("sparkles", size = (size * 0.18f).toInt(), color = Color.White) }
    }
}

/** Ring / arc progress gauge. [value] 0..1, sweeps clockwise from 12 o'clock. */
@Composable
fun RingGauge(
    value: Float,
    color: Color,
    track: Color,
    strokeWidth: Float = 8f,
    size: Int = 120,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val sw = strokeWidth.dp.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, -90f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw))
            drawArc(color, -90f, 360f * value.coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        content?.invoke()
    }
}

/** 5-axis balance radar. Values aligned to the axis order, each 0..1. */
@Composable
fun RadarChart(values: List<Float>, colors: List<Color>, grid: Color, size: Int = 220, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = min(cx, cy) * 0.82f
        val n = values.size
        fun pt(i: Int, rad: Float): Offset {
            val ang = (-90f + i * 360f / n) * (3.14159265f / 180f)
            return Offset(cx + rad * cos(ang), cy + rad * sin(ang))
        }
        // grid rings
        for (ring in 1..4) {
            val rr = r * ring / 4f
            val path = Path()
            for (i in 0 until n) {
                val p = pt(i, rr)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, grid, style = Stroke(0.8.dp.toPx()))
        }
        // spokes
        for (i in 0 until n) drawLine(grid, Offset(cx, cy), pt(i, r), 0.8.dp.toPx())
        // value polygon
        val vp = Path()
        for (i in 0 until n) {
            val p = pt(i, r * values[i].coerceIn(0f, 1f))
            if (i == 0) vp.moveTo(p.x, p.y) else vp.lineTo(p.x, p.y)
        }
        vp.close()
        drawPath(vp, colors.first().copy(alpha = 0.18f))
        drawPath(vp, colors.first(), style = Stroke(2.dp.toPx()))
        for (i in 0 until n) {
            val p = pt(i, r * values[i].coerceIn(0f, 1f))
            drawCircle(colors[i % colors.size], 3.5.dp.toPx(), p)
        }
    }
}

/** Simple sparkline (weekly trends). */
@Composable
fun Sparkline(data: List<Float>, color: Color, modifier: Modifier = Modifier, fill: Boolean = true) {
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val maxV = (data.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
        val minV = data.minOrNull() ?: 0f
        val range = (maxV - minV).coerceAtLeast(0.0001f)
        val stepX = size.width / (data.size - 1)
        fun y(v: Float) = size.height - ((v - minV) / range) * size.height * 0.92f - size.height * 0.04f
        val line = Path()
        data.forEachIndexed { i, v ->
            val px = i * stepX
            val py = y(v)
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        }
        drawPath(line, color, style = Stroke(2.dp.toPx()))
    }
}

/** Vertical bar chart row (weekly minutes / sleep hours). */
@Composable
fun MiniBars(data: List<Float>, color: Color, track: Color, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    Canvas(modifier) {
        val maxV = (data.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
        val gap = size.width * 0.04f
        val bw = (size.width - gap * (data.size - 1)) / data.size
        data.forEachIndexed { i, v ->
            val h = (v / maxV) * size.height
            val x = i * (bw + gap)
            val col = if (highlightLast && i == data.size - 1) color else color.copy(alpha = 0.45f)
            // track
            drawRoundRectCompat(track, x, 0f, bw, size.height, bw / 2.5f)
            drawRoundRectCompat(col, x, size.height - h, bw, h, bw / 2.5f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectCompat(
    color: Color, x: Float, y: Float, w: Float, h: Float, radius: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(w, h.coerceAtLeast(0f)),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )
}
