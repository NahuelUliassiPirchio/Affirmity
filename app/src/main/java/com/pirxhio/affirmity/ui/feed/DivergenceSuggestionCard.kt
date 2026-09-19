package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.pirxhio.affirmity.R

/** A light, non-blocking suggestion; only its explicit actions can change or dismiss state. */
@Composable
fun DivergenceSuggestionCard(
    suggestedGoalId: String,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.divergence_prompt_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            divergenceGoalLabelRes(suggestedGoalId)?.let { labelRes ->
                Text(
                    text = stringResource(
                        R.string.divergence_prompt_suggested_goal,
                        stringResource(labelRes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = actionsEnabled) {
                    Text(stringResource(R.string.divergence_prompt_dismiss))
                }
                TextButton(onClick = onAdd, enabled = actionsEnabled) {
                    Text(stringResource(R.string.divergence_prompt_add))
                }
            }
        }
    }
}

@StringRes
internal fun divergenceGoalLabelRes(goalId: String): Int? = when (goalId) {
    "calm" -> R.string.divergence_goal_calm
    "confidence" -> R.string.divergence_goal_confidence
    "self_love" -> R.string.divergence_goal_self_love
    "motivation" -> R.string.divergence_goal_motivation
    "connection" -> R.string.divergence_goal_connection
    "change" -> R.string.divergence_goal_change
    "direction" -> R.string.divergence_goal_direction
    else -> null
}
