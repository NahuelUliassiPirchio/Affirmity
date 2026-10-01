package com.pirxhio.affirmity.ui.affirmations

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.ui.components.FloatingChromeElevation
import com.pirxhio.affirmity.ui.components.floatingChromeColor

private val IndicatorSize = 32.dp
private val GlyphSize = 15.dp
private val GlyphStroke = 3.dp
private const val PopScale = 1.12f
private const val CrossfadeMillis = 150

/**
 * Visual of the card's Save-to button: a circle with the same container as the feed's streak/avatar
 * pill ([floatingChromeColor] and its elevation, so it follows light/dark), holding a + while
 * nothing is saved and a check once the affirmation is in Favorites or any group, both in the
 * primary colour. The state change crossfades and pops slightly; Compose animations follow the
 * system animator scale. Semantics live on the surrounding button.
 */
@Composable
internal fun SaveToIndicator(saved: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (saved) PopScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "saveScale",
    )
    Surface(
        shape = CircleShape,
        color = floatingChromeColor(),
        tonalElevation = FloatingChromeElevation,
        shadowElevation = FloatingChromeElevation,
        modifier = modifier.size(IndicatorSize).scale(scale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(targetState = saved, animationSpec = tween(CrossfadeMillis), label = "saveGlyph") { isSaved ->
                SaveGlyph(isSaved = isSaved, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Hand-drawn + and check: Material icons have one fixed thin stroke, so both glyphs are stroked
 * here with [GlyphStroke], round caps and joins. Decorative; the button carries the semantics.
 */
@Composable
private fun SaveGlyph(isSaved: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(GlyphSize)) {
        val stroke = Stroke(width = GlyphStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        if (isSaved) {
            val check = Path().apply {
                moveTo(w * 0.10f, h * 0.55f)
                lineTo(w * 0.40f, h * 0.85f)
                lineTo(w * 0.92f, h * 0.18f)
            }
            drawPath(check, color, style = stroke)
        } else {
            drawLine(color, Offset(w * 0.5f, h * 0.05f), Offset(w * 0.5f, h * 0.95f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.05f, h * 0.5f), Offset(w * 0.95f, h * 0.5f), stroke.width, StrokeCap.Round)
        }
    }
}
