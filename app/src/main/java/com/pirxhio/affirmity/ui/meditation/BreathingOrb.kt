package com.pirxhio.affirmity.ui.meditation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.meditation.BreathRole
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

private const val FULL_GUIDE_RING_ALPHA = 0.05f
private const val EMPTY_GUIDE_RING_ALPHA = 0.09f
private const val ORB_FILL_ALPHA = 0.16f
private const val ORB_RIM_ALPHA = 0.55f

/**
 * The breathing shape drawn for a counted breath phase instead of the progress ring and seconds
 * countdown, in the idle backdrop's ring language: two static guide rings mark "full" (1.0) and
 * "empty" ([BREATH_SCALE_MIN]); the orb between them grows on an inhale and shrinks on an exhale.
 * During a hold only the rim thickens -- no pulsing.
 *
 * Timing: a LINEAR progress `p` runs from its current value to 1 over the REMAINING phase time and
 * is mapped through [breathScale] inside the draw lambda only, so the animation never recomposes
 * per frame. The Animatable is recreated on [generation] (a phase change) at the matching fraction; pause and
 * resume are handled by [running] alone and continue from the current value, never re-snapping.
 */
@Composable
fun BreathingOrb(
    role: BreathRole,
    phaseMillis: Long,
    elapsedMillis: Long,
    generation: Int,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    // Keyed on the generation DURING composition, so the role and the progress change in the same
    // frame. Snapping later in an effect would draw one frame of the new role at the old progress
    // (e.g. a full-size flash when an exhale hands over to an inhale).
    val progress = remember(generation) { Animatable(fractionOf(elapsedMillis, phaseMillis)) }

    LaunchedEffect(progress, running) {
        // Holds never change size; animating would only invalidate the draw every frame.
        if (isHoldRole(role) || !running || progress.value >= 1f) return@LaunchedEffect

        // Animations-off snaps to the end size; any other scale (0.5x, 2x) must NOT stretch the
        // animation, or it would drift from the engine clock that owns the phase length.
        val original = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        val clockLocked = object : MotionDurationScale {
            override val scaleFactor: Float = if (original == 0f) 0f else 1f
        }
        val remainingMillis = (phaseMillis * (1f - progress.value)).roundToInt().coerceAtLeast(1)
        withContext(clockLocked) {
            progress.animateTo(1f, tween(durationMillis = remainingMillis, easing = LinearEasing))
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val isHold = isHoldRole(role)
    Box(
        // Decorative: TalkBack relies on the phase label, which already announces inhale/exhale.
        modifier = modifier.clearAndSetSemantics {}.drawBehind {
            val strokeWidth = (if (isHold) 3.dp else 2.dp).toPx()
            // Inset by half the rim so the stroke never overhangs the box.
            val maxRadius = size.minDimension / 2f - strokeWidth / 2f
            drawCircle(primary.copy(alpha = FULL_GUIDE_RING_ALPHA), maxRadius * BREATH_SCALE_MAX, center)
            drawCircle(primary.copy(alpha = EMPTY_GUIDE_RING_ALPHA), maxRadius * BREATH_SCALE_MIN, center)
            val radius = maxRadius * breathScale(role, progress.value)
            drawCircle(primary.copy(alpha = ORB_FILL_ALPHA), radius, center)
            drawCircle(
                color = primary.copy(alpha = ORB_RIM_ALPHA),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        },
    )
}

private fun isHoldRole(role: BreathRole) = role == BreathRole.HOLD_FULL || role == BreathRole.HOLD_EMPTY

private fun fractionOf(elapsedMillis: Long, phaseMillis: Long): Float =
    if (phaseMillis <= 0L) 1f else (elapsedMillis.toFloat() / phaseMillis).coerceIn(0f, 1f)
