package com.example.vulcanizing

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun shortLabel(s: String): String = if (s.length > 9) s.take(8) + "…" else s

@Composable
fun BarChart(labels: List<String>, values: List<Int>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    val barColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width((labels.size * 48).dp.coerceAtLeast(300.dp)).height(220.dp)) {
            val maxV = (values.maxOrNull() ?: 1).coerceAtLeast(1)
            val bottom = size.height - 36.dp.toPx()
            val top = 20.dp.toPx()
            val slot = size.width / labels.size
            val paint = Paint().apply {
                color = textColor.toArgb()
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            for (i in labels.indices) {
                val h = (values[i].toFloat() / maxV) * (bottom - top)
                val left = i * slot + slot * 0.2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, bottom - h),
                    size = Size(slot * 0.6f, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                drawContext.canvas.nativeCanvas.drawText(
                    values[i].toString(), i * slot + slot / 2, bottom - h - 4.dp.toPx(), paint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    labels[i], i * slot + slot / 2, bottom + 16.dp.toPx(), paint
                )
            }
        }
    }
}

@Composable
fun LineChart(labels: List<String>, values: List<Double>, symbol: String, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    var selected by remember(values.size) { mutableIntStateOf(values.size - 1) }
    val sel = selected.coerceIn(0, values.size - 1)
    val tip = labels[sel] + ": " + symbol + String.format(Locale.US, "%,.2f", values[sel])

    Column(modifier) {
        Text(tip, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier
                    .width((values.size * 44).dp.coerceAtLeast(320.dp))
                    .height(200.dp)
                    .pointerInput(values.size) {
                        detectTapGestures { offset ->
                            val slot = size.width.toFloat() / values.size
                            selected = (offset.x / slot).toInt().coerceIn(0, values.size - 1)
                        }
                    }
            ) {
                val current = selected.coerceIn(0, values.size - 1)
                val padTop = 16.dp.toPx()
                val padBottom = 28.dp.toPx()
                val h = size.height - padTop - padBottom
                val maxV = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                val slot = size.width / values.size
                val paint = Paint().apply {
                    color = textColor.toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                for (g in 0..3) {
                    val y = padTop + h * g / 3f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val pts = values.indices.map { i ->
                    Offset(i * slot + slot / 2, padTop + h * (1f - (values[i] / maxV).toFloat()))
                }
                val path = Path()
                pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                pts.forEachIndexed { i, p ->
                    drawCircle(lineColor, if (i == current) 7.dp.toPx() else 4.dp.toPx(), p)
                }
                val step = maxOf(1, values.size / 7)
                labels.forEachIndexed { i, l ->
                    if (i % step == 0 || i == current) {
                        drawContext.canvas.nativeCanvas.drawText(l, pts[i].x, size.height - 8.dp.toPx(), paint)
                    }
                }
            }
        }
    }
}