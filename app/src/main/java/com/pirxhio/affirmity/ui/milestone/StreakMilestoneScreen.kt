package com.pirxhio.affirmity.ui.milestone

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.StreakMilestones

/** Subtitle copy for each celebrated milestone; null for any value without one (e.g. 1, badge-only). */
@StringRes
internal fun milestoneSubtitleRes(days: Int): Int? = when (days) {
    3 -> R.string.streak_milestone_subtitle_3
    7 -> R.string.streak_milestone_subtitle_7
    14 -> R.string.streak_milestone_subtitle_14
    30 -> R.string.streak_milestone_subtitle_30
    60 -> R.string.streak_milestone_subtitle_60
    100 -> R.string.streak_milestone_subtitle_100
    180 -> R.string.streak_milestone_subtitle_180
    365 -> R.string.streak_milestone_subtitle_365
    else -> null
}

/**
 * Full-screen celebration shown once when the general streak hits a milestone. Same structure as
 * [com.pirxhio.affirmity.ui.healer.StreakHealerGrantedScreen].
 */
@Composable
fun StreakMilestoneScreen(
    milestoneDays: Int,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
) {
    val nextMilestone = StreakMilestones.next(milestoneDays)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = stringResource(R.string.streak_milestone_icon_content_description),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = stringResource(R.string.streak_milestone_title, milestoneDays),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            milestoneSubtitleRes(milestoneDays)?.let { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = if (nextMilestone != null) {
                    stringResource(R.string.streak_milestone_explanation_next, nextMilestone)
                } else {
                    stringResource(R.string.streak_milestone_explanation_final)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onConfirm, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.streak_milestone_confirm_button))
            }
        }
    }
}
