package com.pirxhio.affirmity.ui.compass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R

/**
 * Minimal Compass/Reflection answer sheet (Notifications V2 scope-expansion decision, mid-Phase-5
 * apply -- see `NotificationCanceller`'s kdoc): the app had no in-app answer surface for Compass
 * questions at all before this. Reached only from a Compass notification tap (the question's
 * [questionText] comes straight from that notification's own body text, since the copy catalog is
 * Admin-SDK-only and the client never reads it directly -- there is deliberately no client-side
 * question lookup here).
 *
 * The note is optional -- spec only requires the user to be able to acknowledge/reflect on the
 * question, not type anything -- so "Save" is always enabled, unlike [com.pirxhio.affirmity.ui.mood.MoodDayDetailSheet]'s
 * save button, which is gated on a mood selection existing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassAnswerScreen(
    questionText: String,
    onDismiss: () -> Unit,
    onSave: (note: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = questionText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.compass_answer_note_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = { onSave(note.trim().ifEmpty { null }) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.compass_answer_save_button))
            }
        }
    }
}
