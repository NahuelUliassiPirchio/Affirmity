package com.pirxhio.affirmity.ui.mood

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pirxhio.affirmity.R

/**
 * Notifications V2 design §8 ("Mood check-in sheet, scope pinned"): a thin wrapper around
 * [MoodDayDetailSheet] for today's check-in only. `MoodDayDetailSheet` already provides the
 * 5-point emoji scale, optional note, and save button that spec's "Mood Check-In Sheet Flow"
 * requirement asks for — the real gap this closes is framing/reachability, not the widget itself:
 * it swaps the calendar-edit sheet's date label for a "¿Cómo estás?" / "How are you?" header and a
 * "¿Qué pasó hoy?" / "What happened today?" note prompt. Reached from both the Mood notification's
 * deep link (`EXTRA_OPEN_MOOD_PICKER`, unchanged from before this batch) and in-app navigation via
 * [MoodScreen] (D7, user-confirmed final scope — no visual redesign).
 */
@Composable
fun MoodCheckInSheet(
    initialMoodValue: Int?,
    initialNote: String?,
    onDismiss: () -> Unit,
    onSave: (moodValue: Int, note: String?) -> Unit,
) {
    MoodDayDetailSheet(
        dayLabel = stringResource(R.string.mood_checkin_header),
        initialMoodValue = initialMoodValue,
        initialNote = initialNote,
        onDismiss = onDismiss,
        onSave = onSave,
        notePlaceholder = stringResource(R.string.mood_checkin_note_prompt),
    )
}
