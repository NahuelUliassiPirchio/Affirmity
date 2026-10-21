package com.pirxhio.affirmity.ui.meditation

import androidx.compose.animation.core.CubicBezierEasing
import com.pirxhio.affirmity.meditation.BreathRole

/** Radius of the breathing shape when the lungs are empty, relative to full. */
const val BREATH_SCALE_MIN = 0.42f

/** Radius of the breathing shape when the lungs are full. */
const val BREATH_SCALE_MAX = 1.0f

/** Soft in/out ease, like a sine in-out: slow at both turning points of the breath. */
private val BreathEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

/**
 * Size of the breathing shape for [role] at [fraction] (0..1, clamped) of the phase, as a factor
 * of the full radius. Pure so it is JVM-testable; the UI feeds it a LINEAR fraction and the easing
 * lives here, so the same fraction is also the engine-clock position of the phase.
 */
fun breathScale(role: BreathRole, fraction: Float): Float {
    val eased = BreathEasing.transform(fraction.coerceIn(0f, 1f))
    val span = BREATH_SCALE_MAX - BREATH_SCALE_MIN
    return when (role) {
        BreathRole.INHALE -> BREATH_SCALE_MIN + span * eased
        BreathRole.EXHALE -> BREATH_SCALE_MAX - span * eased
        BreathRole.HOLD_FULL -> BREATH_SCALE_MAX
        BreathRole.HOLD_EMPTY -> BREATH_SCALE_MIN
    }
}
