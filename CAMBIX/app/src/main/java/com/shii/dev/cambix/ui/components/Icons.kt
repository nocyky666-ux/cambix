package com.shii.dev.cambix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CxIcon {
    FlashOff, FlashAuto, FlashOn, Timer, Grid, Flip, Camera, Compass, Sliders, Photos, Gear,
    Close, Play, Share, Trash, Back, Live, Info,
    Undo, Redo, Compare, Heart, HeartFilled, Sparkle, Download, Plus, Rotate, Chart, Wand, Level,
}

@Composable
fun CxIconView(icon: CxIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawIcon(icon, tint) }
}

private fun DrawScope.drawIcon(icon: CxIcon, c: Color) {
    val s = size.minDimension
    val strokeWidth = s * 0.085f
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun p(x: Float, y: Float) = Offset(x * s, y * s)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(c, p(x1, y1), p(x2, y2), strokeWidth, StrokeCap.Round)
    }

    fun poly(vararg pts: Float, close: Boolean = false, fill: Boolean = false) {
        val path = Path()
        path.moveTo(pts[0] * s, pts[1] * s)
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(pts[i] * s, pts[i + 1] * s)
            i += 2
        }
        if (close) path.close()
        drawPath(path, c, style = if (fill) Fill else stroke)
    }

    fun bolt(fill: Boolean) =
        poly(0.58f, 0.08f, 0.28f, 0.55f, 0.48f, 0.55f, 0.42f, 0.92f, 0.74f, 0.43f, 0.53f, 0.43f, close = true, fill = fill)

    when (icon) {
        CxIcon.FlashOn -> bolt(true)
        CxIcon.FlashOff -> {
            bolt(false)
            line(0.14f, 0.14f, 0.86f, 0.86f)
        }
        CxIcon.FlashAuto -> {
            bolt(false)
            line(0.78f, 0.36f, 0.86f, 0.12f)
            line(0.86f, 0.12f, 0.94f, 0.36f)
            line(0.81f, 0.27f, 0.91f, 0.27f)
        }
        CxIcon.Timer -> {
            drawCircle(c, s * 0.32f, p(0.5f, 0.58f), style = stroke)
            line(0.5f, 0.58f, 0.5f, 0.4f)
            line(0.4f, 0.14f, 0.6f, 0.14f)
        }
        CxIcon.Grid -> {
            drawRoundRect(c, p(0.14f, 0.14f), Size(s * 0.72f, s * 0.72f), CornerRadius(s * 0.1f), style = stroke)
            line(0.38f, 0.14f, 0.38f, 0.86f)
            line(0.62f, 0.14f, 0.62f, 0.86f)
            line(0.14f, 0.38f, 0.86f, 0.38f)
            line(0.14f, 0.62f, 0.86f, 0.62f)
        }
        CxIcon.Flip, CxIcon.Rotate -> {
            drawArc(
                color = c, startAngle = -60f, sweepAngle = 280f, useCenter = false,
                topLeft = p(0.2f, 0.2f), size = Size(s * 0.6f, s * 0.6f), style = stroke,
            )
            val end = (-60.0 + 280.0) * PI / 180.0
            val tipX = (0.5 + 0.3 * cos(end)).toFloat()
            val tipY = (0.5 + 0.3 * sin(end)).toFloat()
            val dirX = (-sin(end)).toFloat()
            val dirY = cos(end).toFloat()
            for (sign in listOf(-1f, 1f)) {
                val a = 0.55f * sign
                val bx = -(dirX * cos(a) - dirY * sin(a))
                val by = -(dirX * sin(a) + dirY * cos(a))
                line(tipX, tipY, tipX + bx * 0.17f, tipY + by * 0.17f)
            }
        }
        CxIcon.Camera -> {
            drawRoundRect(c, p(0.1f, 0.3f), Size(s * 0.8f, s * 0.54f), CornerRadius(s * 0.12f), style = stroke)
            drawCircle(c, s * 0.16f, p(0.5f, 0.57f), style = stroke)
            poly(0.34f, 0.3f, 0.4f, 0.18f, 0.6f, 0.18f, 0.66f, 0.3f)
        }
        CxIcon.Compass -> {
            drawCircle(c, s * 0.38f, p(0.5f, 0.5f), style = stroke)
            poly(0.66f, 0.34f, 0.56f, 0.56f, 0.34f, 0.66f, 0.44f, 0.44f, close = true)
        }
        CxIcon.Sliders -> {
            line(0.12f, 0.25f, 0.88f, 0.25f)
            line(0.12f, 0.5f, 0.88f, 0.5f)
            line(0.12f, 0.75f, 0.88f, 0.75f)
            drawCircle(c, s * 0.09f, p(0.65f, 0.25f))
            drawCircle(c, s * 0.09f, p(0.35f, 0.5f))
            drawCircle(c, s * 0.09f, p(0.58f, 0.75f))
        }
        CxIcon.Photos -> {
            drawRoundRect(c, p(0.12f, 0.16f), Size(s * 0.76f, s * 0.68f), CornerRadius(s * 0.1f), style = stroke)
            poly(0.12f, 0.74f, 0.4f, 0.46f, 0.58f, 0.64f, 0.7f, 0.54f, 0.88f, 0.74f)
            drawCircle(c, s * 0.06f, p(0.7f, 0.32f))
        }
        CxIcon.Gear -> {
            drawCircle(c, s * 0.14f, p(0.5f, 0.5f), style = stroke)
            drawCircle(c, s * 0.27f, p(0.5f, 0.5f), style = stroke)
            for (i in 0 until 8) {
                val a = i * PI / 4.0
                line(
                    (0.5 + 0.27 * cos(a)).toFloat(), (0.5 + 0.27 * sin(a)).toFloat(),
                    (0.5 + 0.4 * cos(a)).toFloat(), (0.5 + 0.4 * sin(a)).toFloat(),
                )
            }
        }
        CxIcon.Close -> {
            line(0.24f, 0.24f, 0.76f, 0.76f)
            line(0.76f, 0.24f, 0.24f, 0.76f)
        }
        CxIcon.Play -> poly(0.3f, 0.2f, 0.8f, 0.5f, 0.3f, 0.8f, close = true, fill = true)
        CxIcon.Share -> {
            line(0.5f, 0.12f, 0.5f, 0.62f)
            poly(0.34f, 0.28f, 0.5f, 0.12f, 0.66f, 0.28f)
            poly(0.3f, 0.42f, 0.2f, 0.42f, 0.2f, 0.88f, 0.8f, 0.88f, 0.8f, 0.42f, 0.7f, 0.42f)
        }
        CxIcon.Trash -> {
            line(0.2f, 0.26f, 0.8f, 0.26f)
            poly(0.4f, 0.26f, 0.4f, 0.14f, 0.6f, 0.14f, 0.6f, 0.26f)
            poly(0.28f, 0.26f, 0.33f, 0.88f, 0.67f, 0.88f, 0.72f, 0.26f)
            line(0.45f, 0.4f, 0.45f, 0.75f)
            line(0.55f, 0.4f, 0.55f, 0.75f)
        }
        CxIcon.Back -> poly(0.62f, 0.2f, 0.34f, 0.5f, 0.62f, 0.8f)
        CxIcon.Live -> {
            drawCircle(c, s * 0.12f, p(0.5f, 0.5f))
            drawCircle(c, s * 0.27f, p(0.5f, 0.5f), style = stroke)
            drawCircle(
                c, s * 0.4f, p(0.5f, 0.5f),
                style = Stroke(
                    width = s * 0.07f, cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.07f, s * 0.1f)),
                ),
            )
        }
        CxIcon.Undo -> {
            poly(0.38f, 0.22f, 0.18f, 0.42f, 0.38f, 0.62f)
            poly(0.18f, 0.42f, 0.6f, 0.42f, 0.82f, 0.58f, 0.82f, 0.78f)
        }
        CxIcon.Redo -> {
            poly(0.62f, 0.22f, 0.82f, 0.42f, 0.62f, 0.62f)
            poly(0.82f, 0.42f, 0.4f, 0.42f, 0.18f, 0.58f, 0.18f, 0.78f)
        }
        CxIcon.Compare -> {
            drawRoundRect(c, p(0.12f, 0.2f), Size(s * 0.76f, s * 0.6f), CornerRadius(s * 0.08f), style = stroke)
            poly(0.14f, 0.24f, 0.5f, 0.24f, 0.5f, 0.76f, 0.14f, 0.76f, close = true, fill = true)
            line(0.5f, 0.2f, 0.5f, 0.8f)
        }
        CxIcon.Heart, CxIcon.HeartFilled -> {
            val path = Path()
            path.moveTo(0.5f * s, 0.86f * s)
            path.cubicTo(0.2f * s, 0.65f * s, 0.12f * s, 0.45f * s, 0.16f * s, 0.32f * s)
            path.cubicTo(0.2f * s, 0.14f * s, 0.42f * s, 0.12f * s, 0.5f * s, 0.30f * s)
            path.cubicTo(0.58f * s, 0.12f * s, 0.8f * s, 0.14f * s, 0.84f * s, 0.32f * s)
            path.cubicTo(0.88f * s, 0.45f * s, 0.8f * s, 0.65f * s, 0.5f * s, 0.86f * s)
            path.close()
            drawPath(path, c, style = if (icon == CxIcon.HeartFilled) Fill else stroke)
        }
        CxIcon.Sparkle -> poly(0.5f, 0.1f, 0.58f, 0.42f, 0.9f, 0.5f, 0.58f, 0.58f, 0.5f, 0.9f, 0.42f, 0.58f, 0.1f, 0.5f, 0.42f, 0.42f, close = true)
        CxIcon.Download -> {
            line(0.5f, 0.12f, 0.5f, 0.62f)
            poly(0.32f, 0.46f, 0.5f, 0.64f, 0.68f, 0.46f)
            poly(0.18f, 0.72f, 0.18f, 0.86f, 0.82f, 0.86f, 0.82f, 0.72f)
        }
        CxIcon.Plus -> {
            line(0.5f, 0.2f, 0.5f, 0.8f)
            line(0.2f, 0.5f, 0.8f, 0.5f)
        }
        CxIcon.Chart -> {
            line(0.2f, 0.8f, 0.2f, 0.5f)
            line(0.4f, 0.8f, 0.4f, 0.25f)
            line(0.6f, 0.8f, 0.6f, 0.4f)
            line(0.8f, 0.8f, 0.8f, 0.15f)
        }
        CxIcon.Wand -> {
            line(0.18f, 0.82f, 0.6f, 0.4f)
            line(0.72f, 0.12f, 0.72f, 0.36f)
            line(0.6f, 0.24f, 0.84f, 0.24f)
            drawCircle(c, s * 0.04f, p(0.34f, 0.2f))
            drawCircle(c, s * 0.04f, p(0.84f, 0.62f))
        }
        CxIcon.Level -> {
            line(0.1f, 0.5f, 0.34f, 0.5f)
            line(0.66f, 0.5f, 0.9f, 0.5f)
            drawCircle(c, s * 0.12f, p(0.5f, 0.5f), style = stroke)
        }
        CxIcon.Info -> {
            drawCircle(c, s * 0.38f, p(0.5f, 0.5f), style = stroke)
            line(0.5f, 0.46f, 0.5f, 0.7f)
            drawCircle(c, s * 0.045f, p(0.5f, 0.3f))
        }
    }
}
