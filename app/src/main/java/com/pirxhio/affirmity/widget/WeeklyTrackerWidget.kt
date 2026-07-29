package com.pirxhio.affirmity.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.data.DayClock
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.DailyCompletionEntity

private const val EXTRA_START_DESTINATION = "start_destination"
private const val HOME_DESTINATION = "AFIRMACIONES"
private val DAY_LETTERS = listOf("L", "M", "M", "J", "V", "S", "D")

/** Filled color for a completed half; empty color for an incomplete one. */
private val FILLED_COLOR = Color(0xFF00696F)
private val EMPTY_COLOR = Color(0x33FFFFFF)
private val TODAY_RING_COLOR = Color(0xFFFFFFFF)

/**
 * Weekly tracker home-screen widget (spec: home-widget). Reads a snapshot from Room *before*
 * `provideContent` (D6) since Glance sessions are torn down when not visible — the widget is
 * refreshed by explicit `updateAll` calls from [com.pirxhio.affirmity.data.WidgetUpdater] and
 * [DayRolloverWorker], not by live composition.
 */
class WeeklyTrackerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AffirmityDatabase.getInstance(context).dailyCompletionDao()
        val today = DayClock.epochDay()
        val weekStart = DayClock.weekStartEpochDay()
        val hasAny = dao.hasAny()
        val rows = dao.getRange(weekStart, weekStart + 6)
        val todayIndex = (today - weekStart).toInt().coerceIn(0, 6)

        provideContent {
            WeeklyTrackerContent(
                hasAny = hasAny,
                rows = rows,
                weekStart = weekStart,
                todayIndex = todayIndex,
            )
        }
    }
}

/** Pure content composable, exercised directly by `GlanceAppWidgetUnitTest` (D6). */
@androidx.compose.runtime.Composable
internal fun WeeklyTrackerContent(
    hasAny: Boolean,
    rows: List<DailyCompletionEntity>,
    weekStart: Long,
    todayIndex: Int,
) {
    val context = LocalContext.current
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_DESTINATION, HOME_DESTINATION)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(com.pirxhio.affirmity.R.drawable.widget_card_bg))
            .padding(12.dp)
            .clickable(actionStartActivity(tapIntent)),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasAny) {
            EmptyState()
        } else {
            WeekGrid(rows = rows, weekStart = weekStart, todayIndex = todayIndex)
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptyState() {
    Text(
        text = "Empezá hoy",
        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceColorProvider(TODAY_RING_COLOR)),
    )
}

@androidx.compose.runtime.Composable
private fun WeekGrid(rows: List<DailyCompletionEntity>, weekStart: Long, todayIndex: Int) {
    val byDay = rows.associateBy { it.epochDay }

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (offset in 0 until 7) {
                val day = byDay[weekStart + offset]
                DayCell(
                    isToday = offset == todayIndex,
                    affirmationDone = day?.affirmationDone ?: false,
                    meditationDone = day?.meditationDone ?: false,
                )
            }
        }
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
            for (letter in DAY_LETTERS) {
                Text(
                    text = letter,
                    modifier = GlanceModifier.padding(horizontal = 6.dp),
                    style = TextStyle(fontSize = 10.sp, color = GlanceColorProvider(TODAY_RING_COLOR)),
                )
            }
        }
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(text = "Afirmar", style = TextStyle(fontSize = 10.sp, color = GlanceColorProvider(TODAY_RING_COLOR)))
            Text(text = "  ", style = TextStyle(fontSize = 10.sp))
            Text(text = "Meditar", style = TextStyle(fontSize = 10.sp, color = GlanceColorProvider(TODAY_RING_COLOR)))
        }
    }
}

@androidx.compose.runtime.Composable
private fun DayCell(isToday: Boolean, affirmationDone: Boolean, meditationDone: Boolean) {
    Box(modifier = GlanceModifier.size(26.dp).padding(1.dp), contentAlignment = Alignment.Center) {
        if (isToday) {
            Image(
                provider = ImageProvider(com.pirxhio.affirmity.R.drawable.widget_cell_ring),
                contentDescription = null,
                modifier = GlanceModifier.size(26.dp),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceColorProvider(TODAY_RING_COLOR)),
            )
        }
        Image(
            provider = ImageProvider(com.pirxhio.affirmity.R.drawable.half_circle_left),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = androidx.glance.ColorFilter.tint(
                GlanceColorProvider(if (affirmationDone) FILLED_COLOR else EMPTY_COLOR),
            ),
        )
        Image(
            provider = ImageProvider(com.pirxhio.affirmity.R.drawable.half_circle_right),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = androidx.glance.ColorFilter.tint(
                GlanceColorProvider(if (meditationDone) FILLED_COLOR else EMPTY_COLOR),
            ),
        )
    }
}

class WeeklyTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeeklyTrackerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DayRolloverWorker.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DayRolloverWorker.cancel(context)
    }
}
