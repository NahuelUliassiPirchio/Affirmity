package com.pirxhio.affirmity.ui.affirmations

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
private const val PopPeakScale = 1.12f
private const val PopUpMillis = 90
private const val CrossfadeMillis = 150

// Glyph geometry as fractions of the glyph box. The plus spans PlusStart..PlusEnd through the centre;
// the check runs start -> elbow -> end.
private const val PlusStart = 0.05f
private const val PlusCenter = 0.5f
private const val PlusEnd = 0.95f
private const val CheckStartX = 0.10f
private const val CheckStartY = 0.55f
private const val CheckElbowX = 0.40f
private const val CheckElbowY = 0.85f
private const val CheckEndX = 0.92f
private const val CheckEndY = 0.18f

/**
 * Visual of the card's Save-to button: a circle with the same container as the feed's streak/avatar
 * pill ([floatingChromeColor] and its elevation, so it follows light/dark), holding a + while
 * nothing is saved and a check once the affirmation is in Favorites or any group, both in the
 * primary colour. The state change crossfades, and flipping to saved gives the glyph a one-shot pop
 * (up to [PopPeakScale], then back to 1); the circle itself never scales, so its size and shadow stay
 * fixed. A card that is already saved when it appears does not pop. Compose animations follow the
 * system animator scale. Semantics live on the surrounding button.
 */
@Composable
internal fun SaveToIndicator(saved: Boolean, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    var previousSaved by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) {
        val becameSaved = saved && !previousSaved
        previousSaved = saved
        if (becameSaved) {
            pop.animateTo(PopPeakScale, tween(PopUpMillis))
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    Surface(
        shape = CircleShape,
        color = floatingChromeColor(),
        tonalElevation = FloatingChromeElevation,
        shadowElevation = FloatingChromeElevation,
        modifier = modifier.size(IndicatorSize),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(pop.value)) {
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
                moveTo(w * CheckStartX, h * CheckStartY)
                lineTo(w * CheckElbowX, h * CheckElbowY)
                lineTo(w * CheckEndX, h * CheckEndY)
            }
            drawPath(check, color, style = stroke)
        } else {
            drawLine(color, Offset(w * PlusCenter, h * PlusStart), Offset(w * PlusCenter, h * PlusEnd), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * PlusStart, h * PlusCenter), Offset(w * PlusEnd, h * PlusCenter), stroke.width, StrokeCap.Round)
        }
    }
}
