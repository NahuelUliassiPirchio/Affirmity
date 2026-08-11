package com.pirxhio.affirmity.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.HealerActivation
import com.pirxhio.affirmity.data.StreakHealerState
import com.pirxhio.affirmity.data.WeeklyStreak

@Composable
fun ProgressScreen(
    affirmationsStreak: WeeklyStreak,
    meditationStreak: WeeklyStreak,
    streakHealer: StreakHealerState,
    onActivateHealer: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.progress_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Always visible regardless of healer/activation state — distinct from the
                // StreakHealerCard below (spec's `general-streak` domain: alive if either habit
                // was done that day, independent of the per-habit trackers further down).
                GeneralStreakCounter(
                    generalStreakDays = streakHealer.generalStreakDays,
                    isTodayDone = streakHealer.isTodayDone,
                )
                StreakHealerCard(streakHealer = streakHealer, onActivateHealer = onActivateHealer)
                // Bug fix vs. the mockup: Meditación used to render a minutes-goal
                // progress bar here. It now shares the exact same day-circle tracker
                // component as Afirmaciones, just with its own icon/data.
                WeeklyStreakTracker(
                    title = stringResource(R.string.progress_affirmations_label),
                    icon = Icons.Filled.AutoAwesome,
                    streak = affirmationsStreak,
                )
                WeeklyStreakTracker(
                    title = stringResource(R.string.progress_meditation_label),
                    icon = Icons.Filled.Timer,
                    streak = meditationStreak,
                )
            }
        }
    }
}

/** Always-visible general-streak day counter (spec's `general-streak` domain) — separate from the
 * per-habit trackers and from [StreakHealerCard]'s own held/CTA state. */
@Composable
private fun GeneralStreakCounter(generalStreakDays: Int, isTodayDone: Boolean) {
    var showExplanation by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = if (isTodayDone) {
                stringResource(R.string.progress_streak_active_content_description)
            } else {
                stringResource(R.string.progress_streak_pending_content_description)
            },
            tint = if (isTodayDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.progress_general_streak_format, generalStreakDays),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
        IconButton(onClick = { showExplanation = true }, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.progress_general_streak_info_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) { Text(stringResource(R.string.progress_general_streak_dialog_confirm)) }
            },
            title = { Text(stringResource(R.string.progress_general_streak_dialog_title)) },
            text = {
                Text(stringResource(R.string.progress_general_streak_dialog_text))
            },
        )
    }
}

/**
 * The streak-healer CTA (design.md's "StreakHealerCard above the two WeeklyStreakTrackers"):
 * a held badge when no window is open, an explicit activation button when [HealerActivation.Available],
 * and a used-today confirmation when [HealerActivation.UsedToday]. Renders nothing when neither a
 * healer is held nor a window is open, to avoid cluttering the screen for the common case.
 */
@Composable
private fun StreakHealerCard(streakHealer: StreakHealerState, onActivateHealer: () -> Unit) {
    val activation = streakHealer.activation
    if (!streakHealer.healerHeld && activation == HealerActivation.Unavailable) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.progress_streak_healer_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            when (activation) {
                is HealerActivation.Available -> {
                    Text(
                        text = stringResource(R.string.progress_streak_healer_available_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onActivateHealer, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.progress_streak_healer_activate_button))
                    }
                }

                is HealerActivation.UsedToday -> {
                    Text(
                        text = stringResource(R.string.progress_streak_healer_used_today_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HealerActivation.Unavailable -> {
                    // streakHealer.healerHeld is true here (guarded above): show the held badge.
                    Text(
                        text = stringResource(R.string.progress_streak_healer_held_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyStreakTracker(
    title: String,
    icon: ImageVector,
    streak: WeeklyStreak,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.progress_weekly_streak_format, streak.streakDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                streak.dayLabels.forEachIndexed { index, label ->
                    val completed = streak.completedDays.getOrElse(index) { false }
                    val healed = index in streak.healedDays
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = when {
                                    healed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    completed -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                shape = CircleShape
                            )
                            .then(
                                if (!completed && !healed) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (healed) {
                            // Multi-color mending-heart drawable — Unspecified keeps its own
                            // colors instead of being flattened to a single tint.
                            Icon(
                                painter = painterResource(R.drawable.ic_heart_mended),
                                contentDescription = stringResource(R.string.progress_healed_day_content_description),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (completed) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
