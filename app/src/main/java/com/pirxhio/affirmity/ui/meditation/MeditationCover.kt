package com.pirxhio.affirmity.ui.meditation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Decorative native vector artwork from reference section 9, on its original 100-unit grid.
 * The parent card owns the accessible label and click action; this adds no redundant semantics. */
@Composable
internal fun MeditationCover(entry: MeditationCatalogEntry, modifier: Modifier = Modifier) {
    val color = Color(meditationCoverColor(entry.categoryRes))
    val motif = meditationCoverMotif(entry.id)
    val rhythm = meditationCoverRhythm(entry.approxDurationMinutes)
    val paths = remember(motif, rhythm) { coverPaths(motif, rhythm) }
    Canvas(modifier) {
        drawRect(Color(0xFF101318))
        drawRect(Brush.radialGradient(
            colors = listOf(color.copy(alpha = .26f), color.copy(alpha = 0f)),
            center = Offset(size.width / 2, size.height * .46f),
            radius = size.minDimension * .62f,
        ))
        withTransform({ scale(size.width / 100, size.height / 100, pivot = Offset.Zero) }) {
            drawMotif(motif, rhythm, color, paths)
        }
    }
}

/** The reference's `op()`: fading outer strokes never drop below 15%, so long sessions stay legible. */
private fun op(alpha: Float): Float = maxOf(.15f, alpha)

private fun coverPaths(motif: MeditationCoverMotif, n: Int): List<Path> {
    val data = when (motif) {
        MeditationCoverMotif.HORIZON -> listOf("M34,56 A16,16 0 0 1 66,56 Z")
        MeditationCoverMotif.FLAME -> listOf("M50,32 C58,44 60,52 50,62 C40,52 42,44 50,32 Z")
        MeditationCoverMotif.CRESCENT -> listOf("M56,27 A23,23 0 1 0 56,73 A19,19 0 1 1 56,27 Z")
        MeditationCoverMotif.ROOT -> (0 until n).map {
            "M${18 + it * 6},${66 + it * 7} Q50,${56 + it * 7} ${82 - it * 6},${66 + it * 7}"
        }
        MeditationCoverMotif.WAVE -> (0..n).map {
            val y = 38 + it * 10
            "M20,$y L30,${y - 5} L40,${y + 5} L50,${y - 5} L60,${y + 5} L70,${y - 5} L80,$y"
        }
        else -> emptyList()
    }
    return data.map { PathParser().parsePathString(it).toPath() }
}

private fun DrawScope.drawMotif(m: MeditationCoverMotif, n: Int, c: Color, paths: List<Path>) {
    val stroke = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float = 1f) =
        drawLine(c, Offset(x1, y1), Offset(x2, y2), 1.4f, StrokeCap.Round, alpha = alpha)
    fun ring(r: Float, alpha: Float = 1f) = drawCircle(c, r, Offset(50f, 50f), alpha, stroke)
    when (m) {
        MeditationCoverMotif.RINGS -> {
            for (i in 0..n) ring(12f + i * 11, op(1f - i * .22f))
            drawCircle(c, 5f, Offset(50f, 50f))
        }
        MeditationCoverMotif.SQUARES -> {
            for (i in 0..n) {
                val z = 14f + i * 14
                drawRoundRect(c, Offset(50 - z / 2, 50 - z / 2), Size(z, z),
                    CornerRadius(3f + i * 1.5f), style = stroke, alpha = op(1f - i * .22f))
            }
            drawRoundRect(c, Offset(46f, 46f), Size(8f, 8f), CornerRadius(2f))
        }
        MeditationCoverMotif.HORIZON -> {
            drawPath(paths.first(), c, alpha = .85f)
            for (i in 0..n) {
                val w = 60f - i * 12
                line(50 - w / 2, 62f + i * 7, 50 + w / 2, 62f + i * 7, op(.9f - i * .18f))
            }
        }
        MeditationCoverMotif.ROOT -> {
            line(50f, 62f, 50f, 34f)
            drawCircle(c, 4f, Offset(50f, 30f))
            paths.forEachIndexed { i, p -> drawPath(p, c, style = stroke, alpha = op(.95f - i * .22f)) }
        }
        MeditationCoverMotif.CONVERGE -> {
            drawCircle(c, 4f, Offset(50f, 50f))
            repeat(4 + n * 2) { i ->
                val a = i.toDouble() / (4 + n * 2) * PI * 2
                line(50 + cos(a).toFloat() * 38, 50 + sin(a).toFloat() * 38,
                    50 + cos(a).toFloat() * 14, 50 + sin(a).toFloat() * 14, .7f)
            }
        }
        MeditationCoverMotif.FLAME -> {
            repeat(n) { drawCircle(c, 19f + it * 10, Offset(50f, 49f), op(.55f - it * .13f), Stroke(1.2f)) }
            drawPath(paths.first(), c)
            line(40f, 74f, 60f, 74f, .6f)
        }
        MeditationCoverMotif.CRESCENT -> {
            drawPath(paths.first(), c, alpha = .9f)
            listOf(74f to 30f, 80f to 47f, 70f to 62f, 82f to 66f, 66f to 20f)
                .take(n + 1).forEach { (x, y) -> drawCircle(c, 1.6f, Offset(x, y)) }
        }
        MeditationCoverMotif.BANDS -> {
            val k = n + 3
            repeat(k) { i ->
                val y = 24f + i * (52f / (k - 1))
                val w = if (i == 0 || i == k - 1) 24f else 44f
                if (i == k / 2) drawRoundRect(c, Offset(50 - w / 2 - 4, y - 3.5f),
                    Size(w + 8, 7f), CornerRadius(3.5f))
                else line(50 - w / 2, y, 50 + w / 2, y, .55f)
            }
        }
        MeditationCoverMotif.WAVE -> paths.forEachIndexed { i, p ->
            drawPath(p, c, alpha = op(1f - i * .2f), style = stroke)
        }
        MeditationCoverMotif.OVERLAP -> {
            listOf(41f, 59f).forEach { x ->
                drawCircle(c, 17f, Offset(x, 50f), .22f)
                drawCircle(c, 17f, Offset(x, 50f), style = stroke)
            }
            if (n > 2) drawCircle(c, 31f, Offset(50f, 50f), .35f, Stroke(1.1f))
        }
        MeditationCoverMotif.RADIATE -> {
            drawCircle(c, 7f, Offset(50f, 50f))
            for (i in 0..n) drawCircle(c, 15f + i * 9, Offset(50f, 50f), op(.9f - i * .18f),
                Stroke(1.3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(2f + i, 3f + i * 2))))
        }
        MeditationCoverMotif.CONSTELLATION -> {
            listOf(26f to 30f, 44f to 24f, 66f to 28f, 78f to 44f, 30f to 52f,
                72f to 64f, 40f to 70f, 22f to 72f, 60f to 78f, 50f to 62f, 84f to 24f)
                .forEach { (x, y) -> drawCircle(c, 2f, Offset(x, y), .4f) }
            drawCircle(c, 3.4f, Offset(56f, 46f))
            drawCircle(c, 9f, Offset(56f, 46f), .7f, Stroke(1.2f))
        }
    }
}
