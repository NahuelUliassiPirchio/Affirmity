package com.pirxhio.affirmity.ui.meditation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.pirxhio.affirmity.ui.theme.AuroraBlushDarkArgb
import com.pirxhio.affirmity.ui.theme.AuroraBlushLightArgb
import com.pirxhio.affirmity.ui.theme.AuroraSeaDarkArgb
import com.pirxhio.affirmity.ui.theme.AuroraSeaLightArgb
import com.pirxhio.affirmity.ui.theme.AuroraTealDarkArgb
import com.pirxhio.affirmity.ui.theme.AuroraTealLightArgb
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal const val AURORA_FIELD_COUNT = 3

/** Incommensurate loop lengths, so the three fields never fall back into the same arrangement. */
internal val AURORA_PERIODS_SECONDS = listOf(47.0, 59.0, 71.0)

private const val DRIFT_AMPLITUDE_OF_WIDTH = 0.12f

/** Vertical sway relative to horizontal, so the path reads as a slow drift rather than a circle. */
private const val VERTICAL_SWAY_RATIO = 0.8f
private val FIELD_ANCHORS = listOf(Offset(0.2f, 0.25f), Offset(0.8f, 0.5f), Offset(0.35f, 0.82f))
private val FIELD_RADIUS_OF_LARGER_SIDE = listOf(0.75f, 0.65f, 0.85f)
private val FIELD_PHASES = listOf(0.0, 2.1, 4.2)

private const val ENTRY_MILLIS = 1200
private const val WARM_MILLIS = 2500
private const val WARM_FIELD_INDEX = 1

/** One drifting colour field: an opaque tone and the peak alpha it is drawn with at its centre. */
internal class AuroraField(val argb: Long, val alpha: Float) {
    val color: Color = Color(argb)
}

/** Fields in index order: teal, sea-green, blush. Index [WARM_FIELD_INDEX] warms toward the last. */
internal class AuroraPalette(val fields: List<AuroraField>)

internal fun auroraPalette(dark: Boolean): AuroraPalette = if (dark) {
    AuroraPalette(
        listOf(
            AuroraField(AuroraTealDarkArgb, 0.20f),
            AuroraField(AuroraSeaDarkArgb, 0.20f),
            AuroraField(AuroraBlushDarkArgb, 0.18f),
        ),
    )
} else {
    AuroraPalette(
        listOf(
            AuroraField(AuroraTealLightArgb, 0.22f),
            AuroraField(AuroraSeaLightArgb, 0.20f),
            AuroraField(AuroraBlushLightArgb, 0.20f),
        ),
    )
}

/**
 * Centre of field [index] after [elapsedSeconds] of drift: an ellipse around the field's anchor
 * (a fraction of the screen), swaying [VERTICAL_SWAY_RATIO] as far vertically as horizontally.
 * Exactly periodic with [AURORA_PERIODS_SECONDS]; t=0 is the anchor itself, which is also the
 * static (reduce-motion) position.
 */
internal fun auroraFieldCenter(index: Int, elapsedSeconds: Double, width: Float, height: Float): Offset {
    val angle = 2.0 * PI * elapsedSeconds / AURORA_PERIODS_SECONDS[index] + FIELD_PHASES[index]
    val amplitude = DRIFT_AMPLITUDE_OF_WIDTH * width
    val anchor = FIELD_ANCHORS[index]
    // Subtracting the phase-0 point makes t=0 sit exactly on the anchor.
    val phase = FIELD_PHASES[index]
    return Offset(
        x = anchor.x * width + amplitude * (cos(angle) - cos(phase)).toFloat(),
        y = anchor.y * height + VERTICAL_SWAY_RATIO * amplitude * (sin(angle) - sin(phase)).toFloat(),
    )
}

internal fun auroraFieldRadius(index: Int, width: Float, height: Float): Float =
    FIELD_RADIUS_OF_LARGER_SIDE[index] * max(width, height)

/** The drift clock only ticks while the session is running; a pause freezes it in place. */
internal fun advanceAuroraTime(elapsedSeconds: Double, deltaSeconds: Double, running: Boolean): Double =
    if (running) elapsedSeconds + deltaSeconds else elapsedSeconds

/**
 * Ambient backdrop for a guided session: three large, soft colour fields drifting very slowly
 * behind the breathing orb, drawn as radial gradients that fade to transparent (no blur, so it
 * works below API 31). While [warm] -- a hold or a user-released phase -- the sea-green field
 * cross-fades toward blush, and back afterwards. It fades in when first composed.
 *
 * Drift time accumulates only while [running]; with the system animator scale at 0 it never
 * advances, leaving the fields at their t=0 positions. Time, warmth and entry are read inside the
 * draw lambda only, so nothing recomposes per frame.
 */
@Composable
fun AuroraBackdrop(running: Boolean, warm: Boolean, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = remember(dark) { auroraPalette(dark) }
    var elapsedSeconds by remember { mutableDoubleStateOf(0.0) }
    val warmth = remember { Animatable(0f) }
    // Survives configuration change, so a rotation does not replay the fade-in.
    var entered by rememberSaveable { mutableStateOf(false) }
    val entry = remember { Animatable(if (entered) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (entered) return@LaunchedEffect
        entered = true
        entry.animateTo(1f, tween(ENTRY_MILLIS, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    LaunchedEffect(warm) {
        warmth.animateTo(if (warm) 1f else 0f, tween(WARM_MILLIS, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(running) {
        // Same convention as BreathingOrb: a 0 animator scale means reduce motion, so the fields stay put.
        if (!running || coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                elapsedSeconds = advanceAuroraTime(elapsedSeconds, (now - last) / 1_000_000_000.0, running = true)
                last = now
            }
        }
    }

    Canvas(modifier = modifier) {
        val t = elapsedSeconds
        val visible = entry.value
        val warmBlend = warmth.value
        val blush = palette.fields.last()
        palette.fields.forEachIndexed { index, field ->
            // The warm field's alpha blends along with its colour, so the swap never pops in brightness.
            val warmed = index == WARM_FIELD_INDEX
            val color = if (warmed) lerp(field.color, blush.color, warmBlend) else field.color
            val peak = (if (warmed) field.alpha + (blush.alpha - field.alpha) * warmBlend else field.alpha) * visible
            val center = auroraFieldCenter(index, t, size.width, size.height)
            val radius = auroraFieldRadius(index, size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    // Eased falloff: a straight two-stop ramp shows a visible rim at the edge.
                    0f to color.copy(alpha = peak),
                    0.45f to color.copy(alpha = peak * 0.45f),
                    0.75f to color.copy(alpha = peak * 0.12f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
