package com.pirxhio.affirmity.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
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
import com.pirxhio.affirmity.AppDestinations
import com.pirxhio.affirmity.EXTRA_START_DESTINATION
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.data.DayClock
import com.pirxhio.affirmity.data.StreakHealerStats
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.remote.FirestoreDailyCompletionRepository
import com.pirxhio.affirmity.data.remote.FirestorePaths
import com.pirxhio.affirmity.data.remote.FirestoreStreakHealerRepository
import com.pirxhio.affirmity.data.repository.RoomStreakHealerRepository
import kotlinx.coroutines.tasks.await

/**
 * Palette. The habit hues are the previous #C77B58 / #00696F lifted for contrast on the dark card:
 * oklch(0.74 0.11 48) -> #E3956C, oklch(0.72 0.09 195) -> #57B6B6, flame oklch(0.80 0.14 62) ->
 * #FDA856 (OKLab -> linear sRGB -> gamma, all in gamut). The rest are Nocturne tokens: text
 * #E9E9ED, neutral-300/400/500, accent-300 for the healed heart.
 */
private val MEDITATION_COLOR = Color(0xFF57B6B6)
private val AFFIRMATION_COLOR = Color(0xFFE3956C)
private val EMPTY_COLOR = Color(0x38B2B6CA) // neutral-400 at 22%
private val TEXT_COLOR = Color(0xFFE9E9ED)
private val LETTER_COLOR = Color(0xFF9397AB) // neutral-500
private val STREAK_PENDING_NUMBER_COLOR = Color(0xFFCFD3E5) // neutral-300
private val STREAK_CAPTION_COLOR = Color(0xFFB2B6CA) // neutral-400
private val FLAME_LIT_COLOR = Color(0xFFFDA856)
private val FLAME_DIM_COLOR = Color(0xCC9397AB) // neutral-500 at 80%
private val HEALED_HEART_COLOR = Color(0xFFD2CEFD) // accent-300

/** Space the today ring takes around a cell; mirrors the allowance in [computeTrackerLayout]. */
private const val RING_ALLOWANCE_DP = 6

/**
 * Weekly tracker home-screen widget (spec: home-widget). Reads a snapshot from Room *before*
 * `provideContent` (D6) since Glance sessions are torn down when not visible — the widget is
 * refreshed by explicit `updateAll` calls from [com.pirxhio.affirmity.data.WidgetUpdater] and
 * [DayRolloverWorker], not by live composition.
 *
 * [SizeMode.Exact] recomposes with the real current size on every resize, so the content reflows
 * across three height tiers (strip / regular / tall) and scales its cells to the room available;
 * see [computeTrackerLayout].
 */
class WeeklyTrackerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = DayClock.epochDay()
        val weekStart = DayClock.rollingWindowStartEpochDay()
        // Resolved fresh from `context.resources` on every call, never cached at the top level or
        // in a companion object (D6/D8): the widget's Context reflects the per-app locale on API
        // 33+, and this stays correct since it's read at the same moment `provideGlance` runs.
        val dayLetters = DayClock.rollingWindowDayLetters(
            context.resources.getStringArray(com.pirxhio.affirmity.R.array.weekday_letters).toList(),
        )
        val healerStart = StreakHealerStats.healerStartEpochDay(today)
        val rawStreakStart = StreakHealerStats.rawStreakStartEpochDay(today)
        // Widened past the healer's rollout floor: the general-streak count itself is unfloored
        // (only healer grant/heal eligibility uses healerStart), so this needs the full lookback.
        val streakRangeStart = minOf(weekStart, rawStreakStart)
        // Signed-in completions live in Firestore only (fcm-notifications bugfix): reading Room
        // here for a signed-in user showed a permanently stale week, frozen at the migration
        // snapshot, since AffirmityAppState no longer writes completions to Room post-migration.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val hasAny: Boolean
        val allRows: List<DailyCompletionEntity>
        val generalStreakDays: Int
        val isTodayDone: Boolean
        val healedDays: Set<Long>
        if (uid != null) {
            val firestore = FirebaseFirestore.getInstance()
            hasAny = firestore.collection(FirestorePaths.dailyCompletionsCollection(uid))
                .limit(1).get().await().documents.isNotEmpty()
            allRows = FirestoreDailyCompletionRepository(firestore, uid).getRange(streakRangeStart, today)
            val healerUses = FirestoreStreakHealerRepository(firestore, uid).getRange(healerStart, today)
            val streakHealer = StreakHealerStats.evaluate(
                allRows, healerUses, today, healerStart, streakStartEpochDay = rawStreakStart,
            )
            generalStreakDays = streakHealer.generalStreakDays
            isTodayDone = streakHealer.isTodayDone
            healedDays = streakHealer.healedDays
        } else {
            val database = AffirmityDatabase.getInstance(context)
            val dao = database.dailyCompletionDao()
            hasAny = dao.hasAny()
            allRows = dao.getRange(streakRangeStart, today)
            val healerUses = RoomStreakHealerRepository(database.streakHealerUseDao()).getRange(healerStart, today)
            val streakHealer = StreakHealerStats.evaluate(
                allRows, healerUses, today, healerStart, streakStartEpochDay = rawStreakStart,
            )
            generalStreakDays = streakHealer.generalStreakDays
            isTodayDone = streakHealer.isTodayDone
            healedDays = streakHealer.healedDays
        }
        val rows = allRows.filter { it.epochDay in weekStart..(weekStart + 6) }
        val todayIndex = (today - weekStart).toInt().coerceIn(0, 6)

        provideContent {
            WeeklyTrackerContent(
                hasAny = hasAny,
                rows = rows,
                weekStart = weekStart,
                todayIndex = todayIndex,
                dayLetters = dayLetters,
                generalStreakDays = generalStreakDays,
                isTodayDone = isTodayDone,
                healedDays = healedDays,
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
    generalStreakDays: Int = 0,
    isTodayDone: Boolean = false,
    healedDays: Set<Long> = emptySet(),
) {
    val context = LocalContext.current
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_DESTINATION, AppDestinations.AFIRMACIONES.name)
    }
    val size = LocalSize.current
    val streakVisible = hasAny && generalStreakDays > 0
    val layout = computeTrackerLayout(size.width.value, size.height.value, streakVisible, spToDpFor(context))
    val a11y = trackerA11yStrings(context, generalStreakDays)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(com.pirxhio.affirmity.R.drawable.widget_card_bg))
            .padding(layout.padding.dp)
            .clickable(actionStartActivity(tapIntent))
            .semantics { contentDescription = widgetDescription(hasAny, generalStreakDays, isTodayDone, a11y) },
        contentAlignment = Alignment.Center,
    ) {
        if (!hasAny) {
            EmptyState(compact = size.height < 76.dp)
        } else {
            val streak = StreakInfo(generalStreakDays, isTodayDone)
            Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (layout.tier == WidgetTier.Tall) {
                    TallHeader(layout, streak)
                    Spacer(modifier = GlanceModifier.height(12.dp))
                }
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (layout.streakPlacement == StreakPlacement.LeadingRow) {
                        Row(modifier = GlanceModifier.width(layout.leadingStreakWidth.dp), verticalAlignment = Alignment.CenterVertically) {
                            StreakCounter(streak, flameSize = STRIP_STREAK_FLAME_DP.dp, numberSize = layout.leadingStreakSp.sp)
                        }
                        Spacer(modifier = GlanceModifier.width(12.dp))
                    }
                    WeekGrid(
                        modifier = GlanceModifier.defaultWeight(),
                        layout = layout,
                        rows = rows,
                        weekStart = weekStart,
                        todayIndex = todayIndex,
                        dayLetters = dayLetters,
                        healedDays = healedDays,
                        a11y = a11y,
                    )
                    val besideStreak = layout.streakPlacement == StreakPlacement.BesideGrid
                    if (layout.tier != WidgetTier.Tall && (layout.legendVisible || besideStreak)) {
                        Spacer(modifier = GlanceModifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            if (besideStreak) {
                                StreakCounter(streak, flameSize = REGULAR_STREAK_FLAME_DP.dp, numberSize = REGULAR_STREAK_SP.sp)
                                if (layout.legendVisible) Spacer(modifier = GlanceModifier.height(SIDE_COLUMN_GAP_DP.dp))
                            }
                            if (layout.legendVisible) {
                                Legend(layout.legendSizeSp.sp, dotSize = 9.dp, gap = LEGEND_ROW_GAP_DP.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Converts sp to dp with the context's real display metrics, so line heights and digit widths
 * follow the user's font scale (including Android 14+ non-linear scaling). Built from the current
 * context on every composition, never cached.
 */
private fun spToDpFor(context: Context): (Float) -> Float {
    val metrics = context.resources.displayMetrics
    return { sp -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics) / metrics.density }
}

private data class StreakInfo(val days: Int, val isTodayDone: Boolean)

private fun trackerA11yStrings(context: Context, streakDays: Int): TrackerA11yStrings {
    return TrackerA11yStrings(
        bothDone = context.getString(com.pirxhio.affirmity.R.string.widget_day_both_done),
        onlyAffirm = context.getString(com.pirxhio.affirmity.R.string.widget_day_affirm_only),
        onlyMeditate = context.getString(com.pirxhio.affirmity.R.string.widget_day_meditate_only),
        nothing = context.getString(com.pirxhio.affirmity.R.string.widget_day_nothing),
        healed = context.getString(com.pirxhio.affirmity.R.string.widget_day_healed),
        today = context.getString(com.pirxhio.affirmity.R.string.widget_day_today),
        streakUnit = context.getString(streakUnitRes(streakDays)),
        todayDone = context.getString(com.pirxhio.affirmity.R.string.widget_today_done),
        todayPending = context.getString(com.pirxhio.affirmity.R.string.widget_today_pending),
        startToday = context.getString(com.pirxhio.affirmity.R.string.widget_empty_state_label),
    )
}

private fun streakUnitRes(days: Int): Int =
    if (days == 1) com.pirxhio.affirmity.R.string.widget_streak_unit_one
    else com.pirxhio.affirmity.R.string.widget_streak_unit

/** Locale-aware full weekday name for [epochDay] (java.time needs API 26; minSdk is 24). */
private fun weekdayName(context: Context, epochDay: Long): String {
    val calendar = DayClock.calendarForEpochDay(epochDay, Calendar.getInstance())
    val locale: Locale = context.resources.configuration.locales[0]
    return DateFormatSymbols.getInstance(locale).weekdays[calendar.get(Calendar.DAY_OF_WEEK)]
}

@androidx.compose.runtime.Composable
private fun EmptyState(compact: Boolean) {
    // `provideGlance` runs in the app process and hands Glance a real Context, so
    // `context.getString(...)` resolves the same `values/`/`values-en/` catalogs as the rest of
    // the app (D8). `stringResource` (Compose UI) is not usable inside a Glance composition.
    val context = LocalContext.current
    Text(
        text = context.getString(com.pirxhio.affirmity.R.string.widget_empty_state_label),
        style = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = if (compact) 14.sp else 16.sp,
            color = GlanceColorProvider(TEXT_COLOR),
        ),
    )
}

/** Tall tier: the streak (big number + caption) on the left, the legend on the right. */
@androidx.compose.runtime.Composable
private fun TallHeader(layout: TrackerLayout, streak: StreakInfo) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (layout.streakPlacement == StreakPlacement.Header) {
                StreakCounter(streak, flameSize = 34.dp, numberSize = TALL_STREAK_SP.sp)
                val status = context.getString(
                    if (streak.isTodayDone) com.pirxhio.affirmity.R.string.widget_today_done
                    else com.pirxhio.affirmity.R.string.widget_today_pending,
                )
                Text(
                    text = "${context.getString(streakUnitRes(streak.days))} · $status",
                    // One line only: the layout reserves a single caption line (the widget's
                    // content description still carries the full streak and status text).
                    maxLines = 1,
                    style = TextStyle(fontSize = TALL_CAPTION_SP.sp, color = GlanceColorProvider(STREAK_CAPTION_COLOR)),
                )
            }
        }
        Column(modifier = GlanceModifier.padding(top = 4.dp)) {
            Legend(layout.legendSizeSp.sp, dotSize = 10.dp, gap = TALL_LEGEND_GAP_DP.dp)
        }
    }
}

/**
 * Flame + count. Lit (filled, warm, bright number) when today is done; outline and dim while
 * today is still pending. A tintable vector, not the flame emoji: emoji are colored bitmap glyphs
 * and ignore ColorFilter/text color, so they can't be dimmed.
 */
@androidx.compose.runtime.Composable
private fun StreakCounter(streak: StreakInfo, flameSize: Dp, numberSize: TextUnit) {
    val lit = streak.isTodayDone
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(
                if (lit) com.pirxhio.affirmity.R.drawable.ic_streak_flame
                else com.pirxhio.affirmity.R.drawable.ic_streak_flame_outline,
            ),
            contentDescription = null,
            modifier = GlanceModifier.size(flameSize),
            colorFilter = androidx.glance.ColorFilter.tint(
                GlanceColorProvider(if (lit) FLAME_LIT_COLOR else FLAME_DIM_COLOR),
            ),
        )
        Spacer(modifier = GlanceModifier.width(STREAK_FLAME_GAP_DP.dp))
        Text(
            text = "${streak.days}",
            style = TextStyle(
                fontSize = numberSize,
                fontWeight = FontWeight.Medium,
                color = GlanceColorProvider(if (lit) TEXT_COLOR else STREAK_PENDING_NUMBER_COLOR),
            ),
        )
    }
}

/** "Afirmar"/"Meditar" labels, each led by a dot filled on the side of the cell it stands for. */
@androidx.compose.runtime.Composable
private fun Legend(textSize: TextUnit, dotSize: Dp, gap: Dp) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.Start) {
        LegendRow(
            label = context.getString(com.pirxhio.affirmity.R.string.widget_action_affirm_label),
            color = AFFIRMATION_COLOR,
            leftHalfFilled = true,
            textSize = textSize,
            dotSize = dotSize,
        )
        Spacer(modifier = GlanceModifier.height(gap))
        LegendRow(
            label = context.getString(com.pirxhio.affirmity.R.string.widget_action_meditate_label),
            color = MEDITATION_COLOR,
            leftHalfFilled = false,
            textSize = textSize,
            dotSize = dotSize,
        )
    }
}

@androidx.compose.runtime.Composable
private fun LegendRow(label: String, color: Color, leftHalfFilled: Boolean, textSize: TextUnit, dotSize: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = GlanceModifier.size(dotSize)) {
            HalfDisc(left = true, color = if (leftHalfFilled) color else EMPTY_COLOR, size = dotSize)
            HalfDisc(left = false, color = if (leftHalfFilled) EMPTY_COLOR else color, size = dotSize)
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(text = label, style = TextStyle(fontSize = textSize, color = GlanceColorProvider(color)))
    }
}

@androidx.compose.runtime.Composable
private fun HalfDisc(left: Boolean, color: Color, size: Dp) {
    Image(
        provider = ImageProvider(
            if (left) com.pirxhio.affirmity.R.drawable.half_circle_left
            else com.pirxhio.affirmity.R.drawable.half_circle_right,
        ),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = androidx.glance.ColorFilter.tint(GlanceColorProvider(color)),
    )
}

@androidx.compose.runtime.Composable
private fun WeekGrid(
    modifier: GlanceModifier,
    layout: TrackerLayout,
    rows: List<DailyCompletionEntity>,
    weekStart: Long,
    todayIndex: Int,
    dayLetters: List<String>,
    healedDays: Set<Long>,
    a11y: TrackerA11yStrings,
) {
    val context = LocalContext.current
    val byDay = rows.associateBy { it.epochDay }
    // Seven equal-weight columns share the grid width, so cells stay centred however wide it is.
    Column(modifier = modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (offset in 0 until 7) {
                val epochDay = weekStart + offset
                val day = byDay[epochDay]
                val isToday = offset == todayIndex
                val affirmed = day?.affirmationDone ?: false
                val meditated = day?.meditationDone ?: false
                val healed = epochDay in healedDays
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    DayCell(
                        diameter = layout.cellDiameter.dp,
                        isToday = isToday,
                        affirmationDone = affirmed,
                        meditationDone = meditated,
                        isHealed = healed,
                        description = cellDescription(weekdayName(context, epochDay), isToday, affirmed, meditated, healed, a11y),
                    )
                }
            }
        }
        if (layout.showLetters) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (offset in 0 until 7) {
                    val isToday = offset == todayIndex
                    Text(
                        text = dayLetters.getOrElse(offset) { "" },
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            fontSize = layout.letterSizeSp.sp,
                            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                            color = GlanceColorProvider(if (isToday) TEXT_COLOR else LETTER_COLOR),
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DayCell(
    diameter: Dp,
    isToday: Boolean,
    affirmationDone: Boolean,
    meditationDone: Boolean,
    isHealed: Boolean,
    description: String,
) {
    // The box reserves the ring's footprint even for non-today cells, so the row height is stable.
    val footprint = diameter + RING_ALLOWANCE_DP.dp
    Box(
        modifier = GlanceModifier.size(footprint).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            Image(
                provider = ImageProvider(com.pirxhio.affirmity.R.drawable.widget_cell_ring),
                contentDescription = null,
                modifier = GlanceModifier.size(footprint),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceColorProvider(TEXT_COLOR)),
            )
        }
        if (isHealed) {
            // A healed day had zero real activity — the mending heart replaces both halves
            // instead of showing two empty ones, to explain why the streak didn't break.
            Box(
                modifier = GlanceModifier
                    .size(diameter)
                    .background(ImageProvider(com.pirxhio.affirmity.R.drawable.widget_healed_bg)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(com.pirxhio.affirmity.R.drawable.ic_widget_heart),
                    contentDescription = null,
                    modifier = GlanceModifier.size(diameter * 0.5f),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceColorProvider(HEALED_HEART_COLOR)),
                )
            }
        } else {
            HalfDisc(left = true, color = if (affirmationDone) AFFIRMATION_COLOR else EMPTY_COLOR, size = diameter)
            HalfDisc(left = false, color = if (meditationDone) MEDITATION_COLOR else EMPTY_COLOR, size = diameter)
        }
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
