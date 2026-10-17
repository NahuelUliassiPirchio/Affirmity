package com.pirxhio.affirmity.ui.healer

import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.window.DialogProperties
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.ui.theme.AffirmityTheme

/** Which half of the rescue the dialog is showing: the decision, or the confirmation after it. */
enum class RescuePhase { Offer, Mended }

/** M3 emphasized-decelerate, i.e. the exponential ease-out the signature animation is built on. */
private val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val MendDurationMillis = 600

/**
 * One time-bound decision, shown over the main scaffold: yesterday slipped, a saved healer can mend
 * it. Stateless — the host owns [phase], [streakDays] (days at stake) and [healerCount] (healers
 * held *before* applying; the Mended phase shows `healerCount - 1` as remaining).
 *
 * Back press / outside tap is "Not now" ([onDismiss]) while offering and "Continue" ([onDone])
 * once mended.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakRescueDialog(
    streakDays: Int,
    healerCount: Int,
    phase: RescuePhase,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = if (phase == RescuePhase.Offer) onDismiss else onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        StreakRescueContent(
            streakDays = streakDays,
            healerCount = healerCount,
            phase = phase,
            onApply = onApply,
            onDismiss = onDismiss,
            onDone = onDone,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** The dialog surface without the window, so previews and screenshots can render it directly. */
@Composable
internal fun StreakRescueContent(
    streakDays: Int,
    healerCount: Int,
    phase: RescuePhase,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mended = phase == RescuePhase.Mended
    val reduceMotion = rememberReduceMotion()

    // Starting out already mended (rotation, previews) snaps; only an Offer -> Mended flip plays.
    val mendProgress = remember { Animatable(if (mended) 1f else 0f) }
    val heartScale = remember { Animatable(if (mended) 1f else 0.6f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(mended, reduceMotion) {
        if (!mended || mendProgress.value >= 1f) return@LaunchedEffect
        if (reduceMotion) {
            mendProgress.snapTo(1f)
            heartScale.snapTo(1f)
        } else {
            mendProgress.animateTo(1f, tween(MendDurationMillis, easing = ExpoOut))
        }
        // Once, when a live mend completes; never on a recreate that starts out already mended.
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    LaunchedEffect(mended, reduceMotion) {
        if (mended && !reduceMotion) {
            heartScale.animateTo(
                1f,
                spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RescueChain(
                mended = mended,
                mendProgress = mendProgress.value,
                heartScale = heartScale.value,
            )
            Spacer(Modifier.height(24.dp))
            // Offer and Mended differ in height; animating the size hides the jump when the
            // crossfade releases the outgoing body.
            Box(
                modifier = Modifier.animateContentSize(
                    animationSpec = if (reduceMotion) snap() else tween(MendDurationMillis / 3),
                ),
            ) {
                Crossfade(
                    targetState = phase,
                    animationSpec = if (reduceMotion) snap() else tween(MendDurationMillis / 3),
                    label = "streakRescuePhase",
                ) { shown ->
                    when (shown) {
                        RescuePhase.Offer -> OfferBody(streakDays, healerCount, onApply, onDismiss)
                        RescuePhase.Mended -> MendedBody(streakDays, healerCount - 1, onDone)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferBody(
    streakDays: Int,
    healerCount: Int,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Headline(stringResource(R.string.streak_rescue_offer_title))
        Spacer(Modifier.height(8.dp))
        BodyText(
            pluralStringResource(R.plurals.streak_rescue_offer_body, streakDays, streakDays),
        )
        Spacer(Modifier.height(16.dp))
        HealerLine(pluralStringResource(R.plurals.streak_rescue_healer_count, healerCount, healerCount))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.streak_rescue_fine_print),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.streak_rescue_apply))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.streak_rescue_not_now))
        }
    }
}

@Composable
private fun MendedBody(
    streakDays: Int,
    remaining: Int,
    onDone: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Headline(stringResource(R.string.streak_rescue_mended_title))
        Spacer(Modifier.height(8.dp))
        BodyText(
            pluralStringResource(R.plurals.streak_rescue_mended_body, streakDays, streakDays),
        )
        Spacer(Modifier.height(16.dp))
        HealerLine(
            if (remaining > 0) {
                pluralStringResource(R.plurals.streak_rescue_remaining, remaining, remaining)
            } else {
                stringResource(R.string.streak_rescue_none_left)
            },
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.streak_rescue_continue))
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        // TalkBack announces the Offer -> Mended swap without the user hunting for it.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HealerLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_heart_mended),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val NodeSize = 48.dp
private val ConnectorGap = 8.dp

/**
 * Day-before / yesterday / today as a chain with one broken link. [mendProgress] 0..1 strokes the
 * yesterday ring solid and closes the two connectors toward it; [heartScale] springs the mended
 * heart in.
 */
@Composable
private fun RescueChain(
    mended: Boolean,
    mendProgress: Float,
    heartScale: Float,
) {
    val flame = colorResource(R.color.notif_streak_accent)
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val description = stringResource(
        if (mended) R.string.streak_rescue_chain_description_mended
        else R.string.streak_rescue_chain_description_offer,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // One merged description instead of three icons and three labels read separately.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NodeSize),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(NodeSize)) {
                val radius = NodeSize.toPx() / 2
                val gap = ConnectorGap.toPx()
                val stroke = 2.dp.toPx()
                val cy = size.height / 2
                val c0 = size.width / 6
                val c1 = size.width / 2
                val c2 = size.width * 5 / 6
                val todayEdge = c2 - radius
                val connector = lerpColor(outline.copy(alpha = 0.6f), primary, mendProgress)

                // Left connector: stops short of yesterday, then draws closed onto the ring.
                drawLine(
                    color = connector,
                    start = Offset(c0 + radius, cy),
                    end = Offset(lerp(c1 - radius - gap, c1 - radius, mendProgress), cy),
                    strokeWidth = stroke,
                )
                // Right connector: starts past the gap, then draws closed back onto the ring.
                drawLine(
                    color = connector,
                    start = Offset(lerp(c1 + radius + gap, c1 + radius, mendProgress), cy),
                    end = Offset(todayEdge, cy),
                    strokeWidth = stroke,
                )

                val ringRadius = radius - stroke / 2
                val ringTopLeft = Offset(c1 - ringRadius, cy - ringRadius)
                val ringSize = Size(ringRadius * 2, ringRadius * 2)
                if (mendProgress > 0f) {
                    drawCircle(
                        color = primaryContainer.copy(alpha = mendProgress),
                        radius = ringRadius,
                        center = Offset(c1, cy),
                    )
                }
                // Dashed ring fades out under the solid arc that strokes around it.
                drawCircle(
                    color = outline.copy(alpha = 1f - mendProgress),
                    radius = ringRadius,
                    center = Offset(c1, cy),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
                if (mendProgress > 0f) {
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = 360f * mendProgress,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = ringSize,
                        style = Stroke(width = stroke),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(NodeSize)
                            .clip(CircleShape)
                            .background(flame.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_streak_flame),
                            contentDescription = null,
                            tint = flame,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (mended) {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart_mended),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = heartScale
                                    scaleY = heartScale
                                    alpha = mendProgress
                                },
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Same 48dp footprint as the other nodes; a solid hairline keeps yesterday the
                    // only dashed element.
                    Box(
                        modifier = Modifier
                            .size(NodeSize)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_streak_flame_outline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(
                R.string.streak_rescue_day_before,
                R.string.streak_rescue_yesterday,
                R.string.streak_rescue_today,
            ).forEach { label ->
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** True when the user turned system animations off ("Remove animations" sets the scale to 0). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

@Preview(name = "Offer light", showBackground = true)
@Composable
private fun OfferLightPreview() {
    AffirmityTheme(darkTheme = false) {
        StreakRescueContent(7, 2, RescuePhase.Offer, {}, {}, {}, Modifier.padding(16.dp))
    }
}

@Preview(
    name = "Offer dark",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OfferDarkPreview() {
    AffirmityTheme(darkTheme = true) {
        StreakRescueContent(7, 2, RescuePhase.Offer, {}, {}, {}, Modifier.padding(16.dp))
    }
}

@Preview(name = "Mended light", showBackground = true)
@Composable
private fun MendedLightPreview() {
    AffirmityTheme(darkTheme = false) {
        StreakRescueContent(7, 2, RescuePhase.Mended, {}, {}, {}, Modifier.padding(16.dp))
    }
}

@Preview(
    name = "Mended dark",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MendedDarkPreview() {
    AffirmityTheme(darkTheme = true) {
        StreakRescueContent(7, 2, RescuePhase.Mended, {}, {}, {}, Modifier.padding(16.dp))
    }
}
