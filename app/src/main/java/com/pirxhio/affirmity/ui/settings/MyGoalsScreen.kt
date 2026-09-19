package com.pirxhio.affirmity.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.personalization.goals.UserGoal
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Edits a local draft and persists it only from the explicit save action. */
@Composable
fun MyGoalsScreen(
    store: UserGoalsStore,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = {},
) {
    var draftGoalIds by remember(store) { mutableStateOf<Set<String>?>(null) }
    var saveError by remember(store) { mutableStateOf(false) }
    val validGoalIds = remember { UserGoal.all.mapTo(mutableSetOf()) { it.id } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        store.observeGoalIds()
            .catch { error ->
                Log.e(TAG, "goal preferences flow failed", error)
                // A stuck-forever spinner is worse than an empty draft: fall back so the screen is
                // usable and a save simply re-persists an empty set rather than hanging.
                emit(emptySet())
            }
            .collect { persistedGoalIds ->
                if (draftGoalIds == null) {
                    // Drop any id no longer in UserGoal.all (e.g. a retired goal) so it can't be
                    // silently re-persisted forever by a save the user never asked for.
                    draftGoalIds = persistedGoalIds.orEmpty().intersect(validGoalIds)
                }
            }
    }

    val draft = draftGoalIds
    if (draft == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.my_goals_headline),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            Text(
                text = stringResource(R.string.my_goals_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(UserGoal.all, key = { it.id }) { goal ->
            val checked = goal.id in draft
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = {
                            draftGoalIds = if (checked) draft - goal.id else draft + goal.id
                        },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text(
                    text = goal.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        item {
            Button(
                onClick = {
                    saveError = false
                    scope.launch {
                        try {
                            store.saveGoalIds(draft)
                            onSaved()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            Log.e(TAG, "saving goal preferences failed", error)
                            saveError = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.my_goals_save_button))
            }
        }
        if (saveError) {
            item {
                Text(
                    text = stringResource(R.string.my_goals_save_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val TAG = "MyGoalsScreen"
