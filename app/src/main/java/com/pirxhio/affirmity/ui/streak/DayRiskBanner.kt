package com.pirxhio.affirmity.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.DayRiskEvaluator
import com.pirxhio.affirmity.widget.WidgetBand
import com.pirxhio.affirmity.widget.widgetBandFor
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * In-app twin of the weekly widget's end-of-day band: the same evaluator, tone copy and ember
 * palette, shown on every tab while a goal is incomplete or the streak is at risk, until the user
 * hides it for the day. Renders nothing otherwise.
 *
 * @param generalStreakDays the healer-aware count exactly as the widget passes it
 */
@Composable
fun DayRiskBanner(
    meditationDone: Boolean,
    affirmationDone: Boolean,
    generalStreakDays: Int,
    tone: String?,
    dismissedDay: Long?,
    onDismiss: (epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = rememberDayClockReading()
    val state = DayRiskEvaluator.evaluate(
        meditationDone = meditationDone,
        affirmationDone = affirmationDone,
        generalStreakDays = generalStreakDays,
        minuteOfDay = reading.minuteOfDay,
    )
    val band = widgetBandFor(state, tone)
    if (!shouldShowDayRiskBanner(band, dismissedDay, reading.epochDay)) return
    DayRiskBannerContent(
        band = band,
        onDismiss = { onDismiss(reading.epochDay) },
        modifier = modifier,
    )
}

/** Stateless banner body; renders nothing when [band] carries no colors or message. */
@Composable
internal fun DayRiskBannerContent(
    band: WidgetBand,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = band.backgroundColor ?: return
    val messageRes = band.messageRes ?: return
    val messageColor = Color(band.messageColor ?: return)
    val message = stringResource(messageRes)
    val a11y = stringResource(R.string.widget_band_a11y, message)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(background), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = Color(band.flameColor ?: return),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = messageColor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = a11y },
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.day_risk_banner_dismiss_content_description),
                tint = messageColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
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
