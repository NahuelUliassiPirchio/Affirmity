package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Per-universe accent hues for the "See all themes" browse grid. Lives in the UI layer on purpose:
 * [com.pirxhio.affirmity.ui.groups.AffirmationGroup] is produced by the generated taxonomy
 * pipeline (`CatalogTaxonomy.kt`, do-not-edit) and carries no color, and color is a presentation
 * concern anyway. Mid-lightness, mid-chroma hues so the same value works as a low-alpha highlight
 * on both the near-white light surfaces and the near-black dark ones; neighbours in catalog order
 * are kept on distinct hue families so adjacent tiles never read as the same group.
 */
private val UniverseAccents: Map<String, Color> = mapOf(
    "self_worth" to Color(0xFFF2A477), // peach
    "confidence_courage" to Color(0xFFEE7F3B), // tangerine
    "calm_peace" to Color(0xFF6FA8DC), // sky
    "mind_anxiety" to Color(0xFF9D8BE0), // lavender
    "presence_acceptance_control" to Color(0xFF86B98A), // sage
    "gratitude_hope_possibility" to Color(0xFFF2C14E), // sunflower
    "motivation_discipline_responsibility" to Color(0xFFE5634D), // coral
    "work_money_growth" to Color(0xFF3FA37A), // emerald
    "body_energy_wellbeing" to Color(0xFFA5C956), // lime
    "love_desire_romantic_relationships" to Color(0xFFD9547E), // raspberry
    "connection_belonging" to Color(0xFF4FB3BF), // brand-adjacent teal
    "change_loss_new_beginnings" to Color(0xFFB07AA1), // dusty plum
    "expectations_boundaries_freedom" to Color(0xFF5C7FD6), // cornflower
    "purpose_identity_direction" to Color(0xFFE08BC4), // orchid
)

/** Accent for [groupId], falling back to the theme's `primary` for any id the map doesn't know --
 *  a future catalog regeneration adding a universe degrades to on-brand teal instead of crashing
 *  or rendering an unstyled tile. */
@Composable
@ReadOnlyComposable
internal fun universeAccentColor(groupId: String): Color =
    UniverseAccents[groupId] ?: MaterialTheme.colorScheme.primary

/** True when the active scheme is dark -- derived from the surface rather than
 *  `isSystemInDarkTheme()` so a forced/previewed theme is respected. */
@Composable
@ReadOnlyComposable
private fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Icon/ink color for content sitting on an accent highlight: the accent pulled ~half-way toward
 *  `onSurface`, i.e. darkened in light mode and lightened in dark mode. The raw mid-tone accent
 *  alone would drop under the 3:1 non-text minimum for the pale hues (sunflower, lime, peach) on
 *  the light surfaces; the blend keeps the hue identity while holding contrast in both schemes. */
@Composable
@ReadOnlyComposable
internal fun universeAccentInk(accent: Color): Color =
    lerp(accent, MaterialTheme.colorScheme.onSurface, 0.45f)

/** A faint accent wash over `surfaceContainerLow` for the tile container -- enough to give each
 *  tile its own temperature without competing with the highlight behind the icon. */
@Composable
@ReadOnlyComposable
internal fun universeAccentContainer(accent: Color): Color =
    accent.copy(alpha = if (isDarkScheme()) 0.12f else 0.08f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

/** Highlight alpha per scheme: on the near-black dark surface a mid-tone needs a little more
 *  opacity to register at all; on the light surface ~30% keeps ink on top well above 3:1. */
@Composable
@ReadOnlyComposable
private fun highlightAlpha(): Float = if (isDarkScheme()) 0.36f else 0.30f

private const val HIGHLIGHT_POINT_COUNT = 8

/** Seeded, size-independent description of one marker smear: two overlapping passes (like a
 *  highlighter going over the same spot twice), each an ellipse sampled at
 *  [HIGHLIGHT_POINT_COUNT] points with a per-point radius jitter and a slight tilt. Stored as
 *  fractions of the canvas so it can be re-projected onto any size without re-rolling. */
private class HighlightShape(
    val tiltRadians: Float,
    val firstPassJitter: FloatArray,
    val secondPassJitter: FloatArray,
    val secondPassTiltDelta: Float,
    val secondPassOffset: Offset,
) {
    companion object {
        /** Seeded from [seedKey]'s `String.hashCode()`, which the JVM specifies exactly -- the same
         *  group always gets the same smear, across recompositions, process restarts and devices. */
        fun from(seedKey: String): HighlightShape {
            val random = Random(seedKey.hashCode())
            fun jitter() = FloatArray(HIGHLIGHT_POINT_COUNT) { 0.84f + random.nextFloat() * 0.24f }
            return HighlightShape(
                tiltRadians = degreesToRadians(-16f + random.nextFloat() * 22f),
                firstPassJitter = jitter(),
                secondPassJitter = jitter(),
                secondPassTiltDelta = degreesToRadians(-6f + random.nextFloat() * 12f),
                secondPassOffset = Offset(
                    x = -0.05f + random.nextFloat() * 0.10f,
                    y = 0.02f + random.nextFloat() * 0.05f,
                ),
            )
        }

        private fun degreesToRadians(degrees: Float): Float = (degrees * PI / 180.0).toFloat()
    }
}

/**
 * Semi-transparent, hand-drawn-looking highlighter smear in [color], meant to sit behind an icon.
 * Deliberately not a circle/blob: a wide, tilted, wobbly ellipse plus a second, smaller offset
 * pass whose overlap reads darker, the way a real marker does when it doubles back. The shape is
 * derived deterministically from [seedKey] (see [HighlightShape.from]), so it never flickers or
 * reshuffles on recomposition.
 */
@Composable
internal fun HandDrawnHighlight(
    seedKey: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = remember(seedKey) { HighlightShape.from(seedKey) }
    val tint = color.copy(alpha = highlightAlpha())
    Canvas(modifier = modifier) {
        drawPath(
            path = smudgePath(
                size = size,
                jitter = shape.firstPassJitter,
                tiltRadians = shape.tiltRadians,
                radiusXFraction = 0.48f,
                radiusYFraction = 0.30f,
                centerOffsetFraction = Offset.Zero,
            ),
            color = tint,
        )
        drawPath(
            path = smudgePath(
                size = size,
                jitter = shape.secondPassJitter,
                tiltRadians = shape.tiltRadians + shape.secondPassTiltDelta,
                radiusXFraction = 0.40f,
                radiusYFraction = 0.22f,
                centerOffsetFraction = shape.secondPassOffset,
            ),
            color = tint,
        )
    }
}

/** A closed, smooth wobbly ellipse: [jitter].size points on an ellipse of radii
 *  ([radiusXFraction], [radiusYFraction]) x [size], each pushed in/out by its jitter factor, rotated
 *  by [tiltRadians] about the (offset) center, then joined with quadratic curves through the
 *  midpoints between consecutive points (each point acts as the control point) -- the standard
 *  closed-spline trick that guarantees a smooth, self-consistent outline with no cusps. */
private fun smudgePath(
    size: Size,
    jitter: FloatArray,
    tiltRadians: Float,
    radiusXFraction: Float,
    radiusYFraction: Float,
    centerOffsetFraction: Offset,
): Path {
    val centerX = size.width * (0.5f + centerOffsetFraction.x)
    val centerY = size.height * (0.5f + centerOffsetFraction.y)
    val radiusX = size.width * radiusXFraction
    val radiusY = size.height * radiusYFraction
    val cosTilt = cos(tiltRadians)
    val sinTilt = sin(tiltRadians)
    val count = jitter.size

    val points = List(count) { index ->
        val angle = (2.0 * PI * index / count).toFloat()
        val ellipseX = cos(angle) * radiusX * jitter[index]
        val ellipseY = sin(angle) * radiusY * jitter[index]
        Offset(
            x = centerX + ellipseX * cosTilt - ellipseY * sinTilt,
            y = centerY + ellipseX * sinTilt + ellipseY * cosTilt,
        )
    }

    fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

    return Path().apply {
        val start = midpoint(points.last(), points.first())
        moveTo(start.x, start.y)
        points.forEachIndexed { index, control ->
            val end = midpoint(control, points[(index + 1) % count])
            quadraticTo(control.x, control.y, end.x, end.y)
        }
        close()
    }
}
