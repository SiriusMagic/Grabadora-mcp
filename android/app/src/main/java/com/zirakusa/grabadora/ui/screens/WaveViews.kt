package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.data.Waveform
import com.zirakusa.grabadora.ui.theme.RecColors

/**
 * The live waveform while recording: the newest bar at the red playhead in the middle, older ones scrolling to the
 * left, a time ruler on top and the flags as accent lines.
 */
@Composable fun LiveWave(levels: List<Float>, elapsed: Long, markers: List<Long>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = RecColors.Muted, fontSize = 11.sp)
    val bar = RecColors.Text.copy(alpha = 0.78f)
    val lines = RecColors.Muted.copy(alpha = 0.5f)
    val accent = RecColors.Accent
    Canvas(modifier) {
        val step = 3.dp.toPx()
        val ruler = 26.dp.toPx()
        val center = size.width / 2
        val mid = ruler + (size.height - ruler) / 2
        val half = (size.height - ruler) / 2 * 0.92f
        fun x(ms: Long) = center - (elapsed - ms) / Waveform.STEP_MS.toFloat() * step
        val seconds = (size.width / 2 / step / Waveform.RATE).toLong() + 1
        val now = elapsed / 1000
        for (s in maxOf(0, now - seconds)..now + seconds) {
            val tx = x(s * 1000)
            drawLine(lines, Offset(tx, ruler - 8.dp.toPx()), Offset(tx, ruler), 1.dp.toPx())
            drawLine(lines, Offset(tx + step * Waveform.RATE / 2, ruler - 4.dp.toPx()), Offset(tx + step * Waveform.RATE / 2, ruler), 1.dp.toPx())
            if (s % 2 == 0L) {
                val text = measurer.measure(Clock.position(s * 1000), label)
                drawText(text, topLeft = Offset(tx - text.size.width / 2f, 0f))
            }
        }
        drawLine(lines, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        val visible = (center / step).toInt() + 2
        val last = levels.size - 1
        for (i in maxOf(0, last - visible)..last) {
            val bx = center - (last - i) * step
            bars(bx, mid, maxOf(1.5.dp.toPx(), levels[i] * half), bar)
        }
        markers.forEach { m -> flag(x(m), ruler, size.height, accent) }
        drawLine(RecColors.Record, Offset(center, ruler), Offset(center, size.height), 2.dp.toPx())
        drawCircle(RecColors.Record, 4.dp.toPx(), Offset(center, ruler))
    }
}

/**
 * A file's waveform in the player: played bars in the accent, the rest faint, the flags, and the red playhead. A tap
 * or a drag seeks.
 */
@Composable fun FileWave(levels: FloatArray?, progress: Float, markers: List<Float>, modifier: Modifier = Modifier, onSeek: (Float) -> Unit) {
    val played = RecColors.Accent
    val rest = RecColors.Wave
    Canvas(modifier.pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width).coerceIn(0f, 1f)) } }) {
        val top = 14.dp.toPx()
        val step = 4.dp.toPx()
        val mid = top + (size.height - top) / 2
        val half = (size.height - top) / 2 * 0.92f
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val bars = levels?.let { Waveform.normalize(Waveform.downsample(it, count)) }
        val head = size.width * progress.coerceIn(0f, 1f)
        if (bars == null || bars.isEmpty()) drawLine(rest, Offset(0f, mid), Offset(size.width, mid), 2.dp.toPx())
        else bars.forEachIndexed { i, level ->
            val bx = (i + 0.5f) * size.width / bars.size
            bars(bx, mid, maxOf(1.5.dp.toPx(), level * half), if (bx <= head) played else rest)
        }
        markers.forEach { flag(size.width * it, top, size.height, played) }
        drawLine(RecColors.Record, Offset(head, top), Offset(head, size.height), 2.dp.toPx())
        drawCircle(RecColors.Record, 4.dp.toPx(), Offset(head, top))
    }
}

private fun DrawScope.bars(x: Float, mid: Float, half: Float, color: Color) {
    val w = 1.6.dp.toPx()
    drawRoundRect(color, Offset(x - w / 2, mid - half), Size(w, half * 2), CornerRadius(w / 2))
}

/** A flag: a thin line with a small pennant on top. */
private fun DrawScope.flag(x: Float, top: Float, bottom: Float, color: Color) {
    drawLine(color, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx())
    val p = Path().apply { moveTo(x, top - 10.dp.toPx()); lineTo(x + 9.dp.toPx(), top - 6.dp.toPx()); lineTo(x, top - 2.dp.toPx()); close() }
    drawPath(p, color)
    drawLine(color, Offset(x, top - 10.dp.toPx()), Offset(x, top), 1.5.dp.toPx())
}
