package com.pirxhio.affirmity.ui.mood

import com.pirxhio.affirmity.data.MOOD_MAX

/** Emoji shown for mood value 1..5 (index 0 = value 1) — shared by the calendar grid, the
 * distribution bars, and the day-detail picker so they always agree. */
val MOOD_EMOJIS = listOf("😢", "🙁", "😐", "🙂", "🤩")

/** Spanish label shown for mood value 1..5 (index 0 = value 1) in the day-detail sheet. */
val MOOD_LABELS = listOf("Muy mal", "Mal", "Regular", "Bien", "Muy bien")

fun moodEmoji(moodValue: Int): String = MOOD_EMOJIS.getOrElse(moodValue - 1) { MOOD_EMOJIS.last() }

fun moodLabel(moodValue: Int): String = MOOD_LABELS.getOrElse(moodValue - 1) { MOOD_LABELS.last() }

/** Sanity check shared by call sites that index into [MOOD_EMOJIS]/[MOOD_LABELS]. */
internal val MOOD_VALUES = 1..MOOD_MAX

internal fun initialMoodSelection(initialMoodValue: Int?): Int? =
    initialMoodValue?.takeIf { it in MOOD_VALUES }

internal fun canSaveMoodSelection(selectedMood: Int?): Boolean =
    selectedMood != null && selectedMood in MOOD_VALUES

internal data class MoodSelectionForEvent(
    val eventKey: Int,
    val selectedMood: Int?,
)

internal fun initialMoodSelectionForEvent(
    eventKey: Int,
    initialMoodValue: Int?,
): MoodSelectionForEvent = MoodSelectionForEvent(
    eventKey = eventKey,
    selectedMood = initialMoodSelection(initialMoodValue),
)
