package com.pirxhio.affirmity.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.NotificationLogEntry
import com.pirxhio.affirmity.data.local.NotificationLogEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only trail of what the notification pipeline actually did: when each channel's next fire
 * was scheduled, whether the worker ran, and whether it managed to post or got blocked by the OS.
 * Answers "did it try and fail, or did it never even run" without needing logcat.
 */
@Composable
fun NotificationDebugScreen(
    entries: List<NotificationLogEntry>,
    onClear: () -> Unit,
    onSendTestNotification: () -> Unit,
    onSendTestMoodNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(onClick = onSendTestNotification, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.notification_debug_send_test_button))
            }
        }

        item {
            Button(onClick = onSendTestMoodNotification, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.notification_debug_send_test_mood_button))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.notification_debug_clear_history_button)) }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.notification_debug_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        items(entries, key = { it.timestampMillis.toString() + it.event.name }) { entry ->
            NotificationLogRow(entry)
        }
    }
}

@Composable
private fun NotificationLogRow(entry: NotificationLogEntry) {
    val colors = if (entry.event.isProblem) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = entry.channel,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = TIMESTAMP_FORMAT.format(Date(entry.timestampMillis)),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(text = entry.event.label, style = MaterialTheme.typography.bodyMedium)
            if (entry.detail.isNotBlank()) {
                Text(text = entry.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

private val NotificationLogEvent.isProblem: Boolean
    get() = this == NotificationLogEvent.NOTIFY_SKIPPED_PERMISSION || this == NotificationLogEvent.WORKER_FAILED
