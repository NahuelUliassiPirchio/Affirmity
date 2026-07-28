package com.pirxhio.affirmity.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.ChannelSettings

@Composable
fun SettingsScreen(
    reminderSettings: ChannelSettings,
    reflectionSettings: ChannelSettings,
    notificationsPermissionGranted: Boolean,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderWindowChanged: (startMinute: Int, endMinute: Int) -> Unit,
    onReflectionEnabledChanged: (Boolean) -> Unit,
    onReflectionWindowChanged: (startMinute: Int, endMinute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!notificationsPermissionGranted) {
            item { PermissionBanner() }
        }

        item {
            ChannelSettingsCard(
                label = stringResource(id = R.string.settings_reminders_label),
                settings = reminderSettings,
                onEnabledChanged = onReminderEnabledChanged,
                onWindowChanged = onReminderWindowChanged,
            )
        }

        item {
            ChannelSettingsCard(
                label = stringResource(id = R.string.settings_reflection_label),
                settings = reflectionSettings,
                onEnabledChanged = onReflectionEnabledChanged,
                onWindowChanged = onReflectionWindowChanged,
            )
        }
    }
}

@Composable
private fun PermissionBanner() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(id = R.string.settings_permission_banner_text),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(intent)
            }) {
                Text(stringResource(id = R.string.settings_permission_banner_action))
            }
        }
    }
}

@Composable
private fun ChannelSettingsCard(
    label: String,
    settings: ChannelSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onWindowChanged: (startMinute: Int, endMinute: Int) -> Unit,
) {
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.enabled, onCheckedChange = onEnabledChanged)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { editingStart = true }) {
                    Text("${stringResource(id = R.string.settings_window_start_label)}: ${formatMinute(settings.startMinute)}")
                }
                TextButton(onClick = { editingEnd = true }) {
                    Text("${stringResource(id = R.string.settings_window_end_label)}: ${formatMinute(settings.endMinute)}")
                }
            }
        }
    }

    if (editingStart) {
        TimePickerDialogHost(
            initialMinute = settings.startMinute,
            onDismiss = { editingStart = false },
            onConfirm = { newStart ->
                editingStart = false
                val newEnd = maxOf(newStart, settings.endMinute)
                onWindowChanged(newStart, newEnd)
            },
        )
    }

    if (editingEnd) {
        TimePickerDialogHost(
            initialMinute = settings.endMinute,
            onDismiss = { editingEnd = false },
            onConfirm = { newEnd ->
                editingEnd = false
                val newStart = minOf(settings.startMinute, newEnd)
                onWindowChanged(newStart, newEnd)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogHost(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

private fun formatMinute(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)
