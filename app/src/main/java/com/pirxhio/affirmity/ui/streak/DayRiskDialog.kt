package com.pirxhio.affirmity.ui.streak

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.DayRiskDialogChoice
import com.pirxhio.affirmity.data.DayRiskEvaluator
import com.pirxhio.affirmity.widget.WidgetBand
import com.pirxhio.affirmity.widget.WidgetBandPalette
import com.pirxhio.affirmity.widget.widgetBandFor
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * In-app twin of the weekly widget's end-of-day takeover: the same evaluator, tone copy and ember
 * palette, shown once per local day as a centered dialog while a goal is incomplete or the streak
 * is at risk. Renders nothing otherwise. The primary button takes the user to the missing activity
 * ([onCta]); both buttons, back and tapping outside mark the day as handled via [onDismiss].
 *
 * @param generalStreakDays the healer-aware count exactly as the widget passes it
 */
@Composable
fun DayRiskDialog(
    meditationDone: Boolean,
    affirmationDone: Boolean,
    generalStreakDays: Int,
    tone: String?,
    dismissedDay: Long?,
    shownLogKey: MutableState<String?>,
    onDismiss: (epochDay: Long) -> Unit,
    onCta: (DayRiskCtaTarget) -> Unit,
    onEvent: (AnalyticsEvent) -> Unit = {},
) {
    val reading = rememberDayClockReading()
    val state = DayRiskEvaluator.evaluate(
        meditationDone = meditationDone,
        affirmationDone = affirmationDone,
        generalStreakDays = generalStreakDays,
        minuteOfDay = reading.minuteOfDay,
    )
    val band = widgetBandFor(state, tone)
    val target = dayRiskCtaTarget(state)
    val variant = dayRiskDialogVariant(state)
    if (target == null || variant == null || !shouldShowDayRiskBanner(band, dismissedDay, reading.epochDay)) return

    // At most once per (day, variant): [shownLogKey] outlives this composable, so re-entering
    // composition (e.g. the status overlay toggling) must not re-log the exposure.
    LaunchedEffect(reading.epochDay, variant) {
        val key = "${reading.epochDay}:${variant.wireName}"
        if (shownLogKey.value != key) {
            shownLogKey.value = key
            onEvent(AnalyticsEvent.DayRiskDialogShown(variant))
        }
    }
    DayRiskDialogContent(
        band = band,
        ctaTarget = target,
        onCta = {
            onEvent(AnalyticsEvent.DayRiskDialogAction(variant, DayRiskDialogChoice.CTA))
            onDismiss(reading.epochDay)
            onCta(target)
        },
        onLater = {
            onEvent(AnalyticsEvent.DayRiskDialogAction(variant, DayRiskDialogChoice.LATER))
            onDismiss(reading.epochDay)
        },
    )
}

/**
 * Stateless dialog body; renders nothing when [band] carries no colors or message. The message is
 * the dialog title, so TalkBack announces it once as the dialog opens.
 */
@Composable
internal fun DayRiskDialogContent(
    band: WidgetBand,
    ctaTarget: DayRiskCtaTarget,
    onCta: () -> Unit,
    onLater: () -> Unit,
) {
    val background = Color(band.backgroundColor ?: return)
    val messageRes = band.messageRes ?: return
    val ember = Color(band.messageColor ?: return)
    val flame = Color(band.flameColor ?: return)

    AlertDialog(
        onDismissRequest = onLater,
        containerColor = background,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        },
        iconContentColor = flame,
        title = { Text(text = stringResource(messageRes), style = MaterialTheme.typography.headlineSmall) },
        titleContentColor = ember,
        confirmButton = {
            // Ember fill with the ground color as label: 5.8:1 or better on both bands.
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = ember, contentColor = background),
            ) {
                Text(
                    text = stringResource(
                        when (ctaTarget) {
                            DayRiskCtaTarget.MEDITATION -> R.string.day_risk_dialog_cta_meditate
                            DayRiskCtaTarget.AFFIRMATIONS -> R.string.day_risk_dialog_cta_affirmations
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onLater,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(WidgetBandPalette.NUMBER_ON_BAND)),
            ) {
                Text(text = stringResource(R.string.day_risk_dialog_later))
            }
        },
    )
}

/**
 * Device-local day/minute that re-evaluates at the next of {today's threshold, next local midnight}
 * (see [nextDayRiskWakeupMillis]) and whenever the app resumes, since coroutine delays do not
 * advance while the device sleeps.
 */
@Composable
private fun rememberDayClockReading(): DayClockReading {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val reading by produceState(
        initialValue = dayClockReading(System.currentTimeMillis(), ZoneId.systemDefault()),
        resumeTick,
    ) {
        while (true) {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            value = dayClockReading(now, zone)
            delay((nextDayRiskWakeupMillis(now, zone) - now).coerceAtLeast(1L))
        }
    }
    return reading
}
