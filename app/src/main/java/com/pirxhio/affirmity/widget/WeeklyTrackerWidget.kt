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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.data.DayClock
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.remote.FirestoreDailyCompletionRepository
import com.pirxhio.affirmity.data.remote.FirestorePaths
import kotlinx.coroutines.tasks.await

private const val EXTRA_START_DESTINATION = "start_destination"
private const val HOME_DESTINATION = "AFIRMACIONES"

/** Per-habit fill colors; empty color for an incomplete half. */
private val MEDITATION_COLOR = Color(0xFF00696F)
private val AFFIRMATION_COLOR = Color(0xFFC77B58)
private val EMPTY_COLOR = Color(0x33FFFFFF)
private val TODAY_RING_COLOR = Color(0xFFFFFFFF)
private val CELL_SIZE = 26.dp

/** Content-height threshold below which the day-letters row no longer fits without clipping. */
private val LETTERS_ROW_THRESHOLD = 60.dp

/**
 * Weekly tracker home-screen widget (spec: home-widget). Reads a snapshot from Room *before*
 * `provideContent` (D6) since Glance sessions are torn down when not visible — the widget is
 * refreshed by explicit `updateAll` calls from [com.pirxhio.affirmity.data.WidgetUpdater] and
 * [DayRolloverWorker], not by live composition.
 *
 * [SizeMode.Exact] recomposes with the real current size on every resize, so the content can drop
 * the day-letters row instead of clipping when the user drags the widget below its full height. The
 * "Afirmar"/"Meditar" legend sits beside the grid (not stacked below it), so it costs width, not
 * extra height.
 */
class WeeklyTrackerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = DayClock.epochDay()
        val weekStart = DayClock.rollingWindowStartEpochDay()
        val dayLetters = DayClock.rollingWindowDayLetters()
        // Signed-in completions live in Firestore only (fcm-notifications bugfix): reading Room
        // here for a signed-in user showed a permanently stale week, frozen at the migration
        // snapshot, since AffirmityAppState no longer writes completions to Room post-migration.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val hasAny: Boolean
        val rows: List<DailyCompletionEntity>
        if (uid != null) {
            val firestore = FirebaseFirestore.getInstance()
            hasAny = firestore.collection(FirestorePaths.dailyCompletionsCollection(uid))
                .limit(1).get().await().documents.isNotEmpty()
            rows = FirestoreDailyCompletionRepository(firestore, uid).getRange(weekStart, weekStart + 6)
        } else {
            val dao = AffirmityDatabase.getInstance(context).dailyCompletionDao()
            hasAny = dao.hasAny()
            rows = dao.getRange(weekStart, weekStart + 6)
        }
        val todayIndex = (today - weekStart).toInt().coerceIn(0, 6)

        provideContent {
            WeeklyTrackerContent(
                hasAny = hasAny,
                rows = rows,
                weekStart = weekStart,
                todayIndex = todayIndex,
                dayLetters = dayLetters,
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
    dayLetters: List<String>,
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionStartActivity(tapIntent)),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasAny) {
            EmptyState()
        } else {
            WeekGrid(rows = rows, weekStart = weekStart, todayIndex = todayIndex, dayLetters = dayLetters)
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
private fun WeekGrid(rows: List<DailyCompletionEntity>, weekStart: Long, todayIndex: Int, dayLetters: List<String>) {
    val byDay = rows.associateBy { it.epochDay }
    val availableHeight = LocalSize.current.height
    val showLetters = availableHeight >= LETTERS_ROW_THRESHOLD

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Row {
                for (offset in 0 until 7) {
                    val day = byDay[weekStart + offset]
                    DayCell(
                        isToday = offset == todayIndex,
                        affirmationDone = day?.affirmationDone ?: false,
                        meditationDone = day?.meditationDone ?: false,
                    )
                }
            }
            if (showLetters) {
                Row(modifier = GlanceModifier.padding(top = 2.dp)) {
                    for (letter in dayLetters) {
                        Text(
                            text = letter,
                            modifier = GlanceModifier.width(CELL_SIZE),
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = GlanceColorProvider(TODAY_RING_COLOR),
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "Afirmar",
                style = TextStyle(fontSize = 16.sp, color = GlanceColorProvider(AFFIRMATION_COLOR)),
            )
            Text(
                text = "Meditar",
                modifier = GlanceModifier.padding(top = 1.dp),
                style = TextStyle(fontSize = 16.sp, color = GlanceColorProvider(MEDITATION_COLOR)),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun DayCell(isToday: Boolean, affirmationDone: Boolean, meditationDone: Boolean) {
    Box(modifier = GlanceModifier.size(CELL_SIZE), contentAlignment = Alignment.Center) {
        if (isToday) {
            Image(
                provider = ImageProvider(com.pirxhio.affirmity.R.drawable.widget_cell_ring),
                contentDescription = null,
                modifier = GlanceModifier.size(CELL_SIZE),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceColorProvider(TODAY_RING_COLOR)),
            )
        }
        Image(
            provider = ImageProvider(com.pirxhio.affirmity.R.drawable.half_circle_left),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = androidx.glance.ColorFilter.tint(
                GlanceColorProvider(if (affirmationDone) AFFIRMATION_COLOR else EMPTY_COLOR),
            ),
        )
        Image(
            provider = ImageProvider(com.pirxhio.affirmity.R.drawable.half_circle_right),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = androidx.glance.ColorFilter.tint(
                GlanceColorProvider(if (meditationDone) MEDITATION_COLOR else EMPTY_COLOR),
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
