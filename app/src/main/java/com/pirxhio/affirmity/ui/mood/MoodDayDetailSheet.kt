package com.pirxhio.affirmity.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pirxhio.affirmity.R

/**
 * Bottom sheet to log or edit a single day's mood: emoji + label preview, a 1-5 emoji picker, an
 * optional note, and a save button. Handles both "log a missed day" and "edit today" — the caller
 * just supplies whatever mood/note are already on record for [dayLabel] (null when unlogged).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodDayDetailSheet(
    dayLabel: String,
    initialMoodValue: Int?,
    initialNote: String?,
    onDismiss: () -> Unit,
    onSave: (moodValue: Int, note: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedMood by remember { mutableIntStateOf(initialMoodValue ?: 4) }
    var note by remember { mutableStateOf(initialNote.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = moodEmoji(selectedMood), fontSize = 56.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = moodLabel(selectedMood),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MOOD_VALUES.forEach { value ->
                    val selected = value == selectedMood
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { selectedMood = value }
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = CircleShape,
                            )
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = moodEmoji(value), fontSize = 22.sp)
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.mood_detail_notes_label)) },
                placeholder = { Text(stringResource(R.string.mood_detail_notes_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = { onSave(selectedMood, note.trim()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.mood_detail_save_button))
            }
        }
    }
}
