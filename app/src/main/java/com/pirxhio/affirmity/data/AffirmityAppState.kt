package com.pirxhio.affirmity.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pirxhio.affirmity.data.local.AffirmationDao
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.DailyViewCount
import com.pirxhio.affirmity.data.local.StreakSnapshot
import com.pirxhio.affirmity.data.local.TrackerPreferences
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Background for an affirmation card. Only [Color] is exercised by the UI in this
 * pass; [Image] exists to match the README's documented JSON shape
 * (`background.type` == "color" | "image") for a future persistence/download pass.
 */
sealed class AffirmationBackground {
    data class Color(val value: String) : AffirmationBackground()
    data class Image(val localPath: String) : AffirmationBackground()
}

data class Affirmation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val background: AffirmationBackground,
)

/** Mon..Sun completion flags for a weekly habit tracker. */
data class WeeklyStreak(
    val completedDays: List<Boolean>,
    val streakDays: Int,
)

fun Affirmation.backgroundColor(): Color =
    when (val bg = background) {
        is AffirmationBackground.Color -> runCatching {
            Color(android.graphics.Color.parseColor(bg.value))
        }.getOrDefault(Color(0xFF00696F))

        is AffirmationBackground.Image -> Color(0xFF00696F)
    }

private fun AffirmationEntity.toAffirmation(): Affirmation = Affirmation(
    id = id,
    title = title,
    subtitle = subtitle,
    background = if (backgroundType == "image") {
        AffirmationBackground.Image(backgroundValue)
    } else {
        AffirmationBackground.Color(backgroundValue)
    },
)

private fun Affirmation.toEntity(): AffirmationEntity = AffirmationEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    backgroundType = when (background) {
        is AffirmationBackground.Image -> "image"
        is AffirmationBackground.Color -> "color"
    },
    backgroundValue = when (val bg = background) {
        is AffirmationBackground.Image -> bg.localPath
        is AffirmationBackground.Color -> bg.value
    },
)

/** A device-local day count (arbitrary epoch, only used for day-difference math). */
private fun epochDay(calendar: Calendar = Calendar.getInstance()): Long {
    val midnight = (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return midnight.timeInMillis / (24 * 60 * 60 * 1000L)
}

/** Mon..Sun completion for the calendar week containing [today], derived from the streak window. */
private fun StreakSnapshot.toWeeklyStreak(today: Calendar = Calendar.getInstance()): WeeklyStreak {
    val daysSinceMonday = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val monday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysSinceMonday) }
    val windowStart = lastCompletedEpochDay - streakDays + 1
    val completedDays = (0 until 7).map { offset ->
        val dayEpoch = epochDay((monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) })
        streakDays > 0 && dayEpoch in windowStart..lastCompletedEpochDay
    }
    return WeeklyStreak(completedDays = completedDays, streakDays = streakDays)
}

/**
 * Shared in-memory state for the whole app, backed by Room (affirmations) and DataStore
 * (trackers) — see README "Decisions". Screens read plain [Affirmation]/[WeeklyStreak] state;
 * this class owns translating that to/from the persisted shapes.
 */
class AffirmityAppState(
    private val scope: CoroutineScope,
    private val affirmationDao: AffirmationDao,
    private val trackerPreferences: TrackerPreferences,
) {
    val affirmations = mutableStateListOf<Affirmation>()

    var affirmationsStreak = mutableStateOf(WeeklyStreak(completedDays = List(7) { false }, streakDays = 0))
        private set

    var meditationStreak = mutableStateOf(WeeklyStreak(completedDays = List(7) { false }, streakDays = 0))
        private set

    /** Null until DataStore finishes its first read; the screen falls back to its own default. */
    var meditationDurationSeconds = mutableStateOf<Int?>(null)
        private set

    private var affirmationsLastCompletedEpochDay = -1L
    private var meditationLastCompletedEpochDay = -1L
    private var affirmationsViewedToday = DailyViewCount(epochDay = -1L, count = 0)

    init {
        scope.launch {
            affirmationDao.observeAll().collect { entities ->
                affirmations.clear()
                affirmations.addAll(entities.map { it.toAffirmation() })
            }
        }
        scope.launch {
            trackerPreferences.observeAffirmationsStreak().collect { snapshot ->
                affirmationsLastCompletedEpochDay = snapshot.lastCompletedEpochDay
                affirmationsStreak.value = snapshot.toWeeklyStreak()
            }
        }
        scope.launch {
            trackerPreferences.observeMeditationStreak().collect { snapshot ->
                meditationLastCompletedEpochDay = snapshot.lastCompletedEpochDay
                meditationStreak.value = snapshot.toWeeklyStreak()
            }
        }
        scope.launch {
            trackerPreferences.observeAffirmationsViewedToday().collect { viewed ->
                affirmationsViewedToday = viewed
            }
        }
        scope.launch {
            trackerPreferences.observeMeditationDurationSeconds().collect { seconds ->
                meditationDurationSeconds.value = seconds
            }
        }
    }

    fun addAffirmation(title: String, subtitle: String, colorHex: String) {
        scope.launch {
            affirmationDao.insert(
                Affirmation(
                    title = title,
                    subtitle = subtitle,
                    background = AffirmationBackground.Color(colorHex),
                ).toEntity()
            )
        }
    }

    fun removeAffirmation(id: String) {
        scope.launch { affirmationDao.deleteById(id) }
    }

    /** Call once per affirmation the user settles on while swiping the feed. */
    fun recordAffirmationViewed() {
        scope.launch {
            val today = epochDay()
            val viewed = affirmationsViewedToday
            val updated = if (viewed.epochDay == today) {
                viewed.copy(count = viewed.count + 1)
            } else {
                DailyViewCount(epochDay = today, count = 1)
            }
            affirmationsViewedToday = updated
            trackerPreferences.saveAffirmationsViewedToday(updated)
            if (updated.count >= AFFIRMATIONS_GOAL_PER_DAY) {
                markDayCompleted(today, isMeditation = false)
            }
        }
    }

    /** Call when a meditation session finishes its full countdown. */
    fun recordMeditationCompleted() {
        scope.launch { markDayCompleted(epochDay(), isMeditation = true) }
    }

    /** Call whenever the user settles on a new duration (slider release, preset tap). */
    fun recordMeditationDurationSelected(seconds: Int) {
        meditationDurationSeconds.value = seconds
        scope.launch { trackerPreferences.saveMeditationDurationSeconds(seconds) }
    }

    private suspend fun markDayCompleted(today: Long, isMeditation: Boolean) {
        val lastEpoch = if (isMeditation) meditationLastCompletedEpochDay else affirmationsLastCompletedEpochDay
        if (lastEpoch == today) return

        val currentStreak = if (isMeditation) meditationStreak.value.streakDays else affirmationsStreak.value.streakDays
        val newStreakDays = if (lastEpoch == today - 1) currentStreak + 1 else 1
        val snapshot = StreakSnapshot(streakDays = newStreakDays, lastCompletedEpochDay = today)

        if (isMeditation) {
            meditationLastCompletedEpochDay = today
            meditationStreak.value = snapshot.toWeeklyStreak()
            trackerPreferences.saveMeditationStreak(snapshot)
        } else {
            affirmationsLastCompletedEpochDay = today
            affirmationsStreak.value = snapshot.toWeeklyStreak()
            trackerPreferences.saveAffirmationsStreak(snapshot)
        }
    }

    private companion object {
        const val AFFIRMATIONS_GOAL_PER_DAY = 2
    }
}

@Composable
fun rememberAffirmityAppState(): AffirmityAppState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        val database = AffirmityDatabase.getInstance(context)
        AffirmityAppState(
            scope = scope,
            affirmationDao = database.affirmationDao(),
            trackerPreferences = TrackerPreferences(context),
        )
    }
}
