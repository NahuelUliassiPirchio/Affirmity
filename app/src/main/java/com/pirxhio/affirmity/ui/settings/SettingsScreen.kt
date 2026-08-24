package com.pirxhio.affirmity.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.QuietHoursSettings

/** Follow-system default plus the two supported explicit languages (spec: `In-App Language
 * Selection`). Maps to/from a BCP-47 language tag rather than [LocaleListCompat] directly so the
 * mapping itself stays a pure, JVM-testable function (D5). */
enum class LanguageOption {
    SYSTEM,
    SPANISH,
    ENGLISH;

    fun toLanguageTag(): String? = when (this) {
        SYSTEM -> null
        SPANISH -> "es"
        ENGLISH -> "en"
    }

    companion object {
        fun fromLanguageTag(tag: String?): LanguageOption = when (tag) {
            "es" -> SPANISH
            "en" -> ENGLISH
            else -> SYSTEM
        }
    }
}

@Composable
fun SettingsScreen(
    reminderSettings: ChannelSettings,
    reflectionSettings: ChannelSettings,
    moodSettings: ChannelSettings,
    quietHoursSettings: QuietHoursSettings,
    notificationsPermissionGranted: Boolean,
    authState: AuthState,
    syncError: String? = null,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderSegmentsChanged: (Set<DaySegment>) -> Unit,
    onReflectionEnabledChanged: (Boolean) -> Unit,
    onReflectionSegmentsChanged: (Set<DaySegment>) -> Unit,
    onMoodEnabledChanged: (Boolean) -> Unit,
    onMoodSegmentsChanged: (Set<DaySegment>) -> Unit,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    onQuietHoursWindowChanged: (startMinute: Int, endMinute: Int) -> Unit,
    onOpenNotificationDebug: () -> Unit,
    onOpenOnboardingGuide: () -> Unit,
    onSignInClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    tier: AccessTier,
    onUpgradeClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
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
                onSegmentsChanged = onReminderSegmentsChanged,
            )
        }

        item {
            ChannelSettingsCard(
                label = stringResource(id = R.string.settings_reflection_label),
                settings = reflectionSettings,
                onEnabledChanged = onReflectionEnabledChanged,
                onSegmentsChanged = onReflectionSegmentsChanged,
            )
        }

        item {
            ChannelSettingsCard(
                label = stringResource(id = R.string.settings_mood_label),
                settings = moodSettings,
                onEnabledChanged = onMoodEnabledChanged,
                onSegmentsChanged = onMoodSegmentsChanged,
            )
        }

        item {
            QuietHoursCard(
                settings = quietHoursSettings,
                onEnabledChanged = onQuietHoursEnabledChanged,
                onWindowChanged = onQuietHoursWindowChanged,
            )
        }

        item {
            LanguageSettingsCard()
        }

        item {
            SubscriptionSection(
                tier = tier,
                onUpgradeClick = onUpgradeClick,
                onManageSubscriptionClick = onManageSubscriptionClick,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(id = R.string.settings_onboarding_guide_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenOnboardingGuide) { Text(stringResource(id = R.string.settings_onboarding_guide_open_button)) }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(id = R.string.settings_notification_debug_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenNotificationDebug) { Text(stringResource(id = R.string.settings_notification_debug_view_history_button)) }
                }
            }
        }

        when (settingsAccountSectionMode(authState)) {
            SettingsAccountSectionMode.SIGNED_IN -> item {
                SignOutSection(
                    authState = authState as AuthState.SignedIn,
                    onSignOutClicked = onSignOutClicked,
                    syncError = syncError,
                )
            }

            SettingsAccountSectionMode.SIGNED_OUT -> item {
                SignInRow(onSignInClicked = onSignInClicked)
            }
        }
    }
}

/**
 * Bug 1 fix: guests who finished onboarding via "continue without account" (`AuthState.SignedOut`
 * survives onboarding) previously had no way back into an account -- this section only ever
 * rendered [SignOutSection], gated on `authState is AuthState.SignedIn`, with no `else` branch.
 * [settingsAccountSectionMode] is the pure branch-selection logic, extracted so it's JVM-testable
 * without composing (see `SettingsAccountSectionModeTest`).
 */
enum class SettingsAccountSectionMode { SIGNED_IN, SIGNED_OUT }

fun settingsAccountSectionMode(authState: AuthState): SettingsAccountSectionMode =
    if (authState is AuthState.SignedIn) {
        SettingsAccountSectionMode.SIGNED_IN
    } else {
        SettingsAccountSectionMode.SIGNED_OUT
    }

@Composable
private fun SignInRow(onSignInClicked: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSignInClicked) {
                Text(stringResource(id = R.string.settings_account_sign_in_button))
            }
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

/**
 * System / Español / English toggle (spec: `In-App Language Selection`). Reads
 * [AppCompatDelegate.getApplicationLocales] fresh on every composition (not `remember`-cached), so
 * the selected option reflects reality again after a process restart. Writing calls
 * [AppCompatDelegate.setApplicationLocales], which is the single source of truth — no parallel
 * DataStore (D5) — and triggers the Activity recreate that re-resolves every `stringResource` in
 * the app (D5's reactivity model).
 */
@Composable
private fun LanguageSettingsCard() {
    val currentTag = AppCompatDelegate.getApplicationLocales().let { locales ->
        if (locales.isEmpty) null else locales[0]?.language
    }
    val selected = LanguageOption.fromLanguageTag(currentTag)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(id = R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption.entries.forEach { option ->
                    val label = when (option) {
                        LanguageOption.SYSTEM -> stringResource(id = R.string.settings_language_system)
                        LanguageOption.SPANISH -> stringResource(id = R.string.settings_language_spanish)
                        LanguageOption.ENGLISH -> stringResource(id = R.string.settings_language_english)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = {
                                    val localeList = option.toLanguageTag()?.let { LocaleListCompat.forLanguageTags(it) }
                                        ?: LocaleListCompat.getEmptyLocaleList()
                                    AppCompatDelegate.setApplicationLocales(localeList)
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelSettingsCard(
    label: String,
    settings: ChannelSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onSegmentsChanged: (Set<DaySegment>) -> Unit,
) {
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DaySegment.entries.forEach { segment ->
                    val selected = segment in settings.segments
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSegmentsChanged(
                                if (selected) settings.segments - segment else settings.segments + segment,
                            )
                        },
                        label = { Text(stringResource(id = segmentLabelRes(segment))) },
                    )
                }
            }
        }
    }
}

private fun segmentLabelRes(segment: DaySegment): Int = when (segment) {
    DaySegment.MADRUGADA -> R.string.settings_segment_madrugada
    DaySegment.MANANA -> R.string.settings_segment_manana
    DaySegment.TARDE -> R.string.settings_segment_tarde
    DaySegment.NOCHE -> R.string.settings_segment_noche
}

/**
 * Global mute-everything window with a free-form start/end time range (unlike the fixed day-part
 * chips above): [endMinute] may be less than [startMinute] to express a range that wraps past
 * midnight (e.g. 23:00-07:00), so this deliberately does NOT clamp end >= start.
 */
@Composable
private fun QuietHoursCard(
    settings: QuietHoursSettings,
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
                Text(text = stringResource(id = R.string.settings_quiet_hours_label), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.enabled, onCheckedChange = onEnabledChanged)
            }
            Text(
                text = stringResource(id = R.string.settings_quiet_hours_description),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { editingStart = true }) {
                    Text("${stringResource(id = R.string.settings_quiet_hours_start_label)}: ${formatMinute(settings.startMinute)}")
                }
                TextButton(onClick = { editingEnd = true }) {
                    Text("${stringResource(id = R.string.settings_quiet_hours_end_label)}: ${formatMinute(settings.endMinute)}")
                }
            }
        }
    }

    if (editingStart) {
        QuietHoursTimePickerDialog(
            initialMinute = settings.startMinute,
            onDismiss = { editingStart = false },
            onConfirm = { newStart ->
                editingStart = false
                onWindowChanged(newStart, settings.endMinute)
            },
        )
    }

    if (editingEnd) {
        QuietHoursTimePickerDialog(
            initialMinute = settings.endMinute,
            onDismiss = { editingEnd = false },
            onConfirm = { newEnd ->
                editingEnd = false
                onWindowChanged(settings.startMinute, newEnd)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursTimePickerDialog(
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
                    TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.settings_time_picker_cancel)) }
                    Button(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(id = R.string.settings_time_picker_ok)) }
                }
            }
        }
    }
}

private fun formatMinute(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)
