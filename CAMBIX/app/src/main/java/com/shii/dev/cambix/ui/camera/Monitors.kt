package com.shii.dev.cambix.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

enum class MonitorKind(val label: String) {
    OFF("Mati"),
    HISTOGRAM("Histogram"),
    WAVEFORM("Waveform"),
    VECTOR("Vektor"),
}

const val HIST_BINS = 64

class MonitorData(
    val luma: IntArray,
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val waveform: IntArray,
    val columns: Int,
    val chroma: FloatArray,
    val clippedHigh: Float,
    val clippedLow: Float,
)

/** Reads a tiny frame (about 48x64), so analysing it four times a second costs next to nothing. */
object MonitorAnalysis {
    fun analyze(px: IntArray, w: Int, h: Int): MonitorData {
        val luma = IntArray(HIST_BINS)
        val red = IntArray(HIST_BINS)
        val green = IntArray(HIST_BINS)
        val blue = IntArray(HIST_BINS)
        val wave = IntArray(w * HIST_BINS)
        val chroma = FloatArray(w * h * 2)
        var high = 0
        var low = 0
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = px[y * w + x]
                val r = ((p shr 16) and 255) / 255f
                val g = ((p shr 8) and 255) / 255f
                val b = (p and 255) / 255f
                val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val bin = (l * (HIST_BINS - 1)).toInt().coerceIn(0, HIST_BINS - 1)
                luma[bin]++
                red[(r * (HIST_BINS - 1)).toInt().coerceIn(0, HIST_BINS - 1)]++
                green[(g * (HIST_BINS - 1)).toInt().coerceIn(0, HIST_BINS - 1)]++
                blue[(b * (HIST_BINS - 1)).toInt().coerceIn(0, HIST_BINS - 1)]++
                wave[x * HIST_BINS + bin]++
                if (l > 0.97f) high++
                if (l < 0.03f) low++
                chroma[i++] = (b - l) * 0.564f
                chroma[i++] = (r - l) * 0.713f
            }
        }
        val total = (w * h).toFloat().coerceAtLeast(1f)
        return MonitorData(luma, red, green, blue, wave, w, chroma, high / total, low / total)
    }
}

@Composable
fun MonitorCard(kind: MonitorKind, data: MonitorData?, modifier: Modifier = Modifier) {
    if (kind == MonitorKind.OFF) return
    val width: Dp = if (kind == MonitorKind.VECTOR) 120.dp else 156.dp
    val height: Dp = if (kind == MonitorKind.VECTOR) 120.dp else 92.dp
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(8.dp)) {
        Canvas(Modifier.size(width, height)) {
            val d = data ?: return@Canvas
            when (kind) {
                MonitorKind.HISTOGRAM -> {
                    val peak = max(1, d.luma.max())
                    val step = size.width / HIST_BINS
                    drawBins(d.red, peak, step, Color(0x66FF5A52))
                    drawBins(d.green, peak, step, Color(0x6666E07A))
                    drawBins(d.blue, peak, step, Color(0x664D9BFF))
                    drawBins(d.luma, peak, step, Color(0xCCFFFFFF))
                }
                MonitorKind.WAVEFORM -> {
                    val peak = max(1, d.waveform.max())
                    val cw = size.width / d.columns
                    val bh = size.height / HIST_BINS
                    for (x in 0 until d.columns) {
                        for (b in 0 until HIST_BINS) {
                            val c = d.waveform[x * HIST_BINS + b]
                            if (c == 0) continue
                            val a = (0.25f + 0.75f * c / peak).coerceIn(0f, 1f)
                            drawRect(
                                Color(0f, 1f, 0.5f, a),
                                topLeft = Offset(x * cw, size.height - (b + 1) * bh),
                                size = androidx.compose.ui.geometry.Size(cw + 0.5f, bh + 0.5f),
                            )
                        }
                    }
                }
                MonitorKind.VECTOR -> {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(Color(0x55FFFFFF), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawLine(Color(0x33FFFFFF), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
                    drawLine(Color(0x33FFFFFF), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
                    var i = 0
                    while (i + 1 < d.chroma.size) {
                        val x = c.x + d.chroma[i] * 2f * r
                        val y = c.y - d.chroma[i + 1] * 2f * r
                        drawCircle(Color(0x99FFFFFF), 1.2.dp.toPx(), Offset(x, y))
                        i += 2
                    }
                }
                MonitorKind.OFF -> {}
            }
        }
        if (data != null && kind != MonitorKind.VECTOR) {
            val warn = when {
                data.clippedHigh > 0.02f -> "Highlight terpotong ${(data.clippedHigh * 100).toInt()}%"
                data.clippedLow > 0.05f -> "Bayangan terpotong ${(data.clippedLow * 100).toInt()}%"
                else -> null
            }
            if (warn != null) CxText(warn, size = 11.sp, weight = FontWeight.SemiBold, color = Cx.Warn, maxLines = 1)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBins(bins: IntArray, peak: Int, step: Float, color: Color) {
    for (i in bins.indices) {
        val hgt = size.height * bins[i] / peak
        drawRect(color, Offset(i * step, size.height - hgt), androidx.compose.ui.geometry.Size(step + 0.5f, hgt))
    }
}

/** Roll angle in degrees from gravity, smoothed. 0 means the phone is level in portrait. */
@Composable
fun rememberRollDegrees(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    val roll = remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val deg = Math.toDegrees(atan2(event.values[0].toDouble(), event.values[1].toDouble())).toFloat()
                roll.floatValue = roll.floatValue * 0.8f + deg * 0.2f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return roll
}

/** A line that stays level with the horizon; it turns green inside 1.5 degrees. */
@Composable
fun HorizonOverlay(rollDegrees: Float, modifier: Modifier = Modifier) {
    val level = abs(rollDegrees) < 1.5f
    val color = if (level) Cx.Ok else Color(0xCCFFFFFF)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            rotate(rollDegrees, c) {
                drawLine(color, Offset(size.width * 0.18f, c.y), Offset(size.width * 0.42f, c.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.58f, c.y), Offset(size.width * 0.82f, c.y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Box(Modifier.padding(top = 36.dp).align(Alignment.Center)) {
            CxText("${abs(rollDegrees).toInt()}\u00B0", size = 12.sp, weight = FontWeight.SemiBold, color = color)
        }
    }
}
