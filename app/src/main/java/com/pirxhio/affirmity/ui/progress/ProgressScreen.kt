package com.pirxhio.affirmity.ui.progress

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.WeeklyStreak

private val dayLabels = listOf("L", "M", "M", "J", "V", "S", "D")

private val swatches = listOf(
    "#2A9D8F", "#00696F", "#5BBCC3", "#5E5E5E", "#8F4D22", "#BA1A1A"
)

@Composable
fun ProgressScreen(
    affirmations: List<Affirmation>,
    affirmationsStreak: WeeklyStreak,
    meditationStreak: WeeklyStreak,
    onAddAffirmation: (title: String, subtitle: String, colorHex: String) -> Unit,
    onDeleteAffirmation: (id: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Mi Progreso",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Hábitos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Bug fix vs. the mockup: Meditación used to render a minutes-goal
                // progress bar here. It now shares the exact same day-circle tracker
                // component as Afirmaciones, just with its own icon/data.
                WeeklyStreakTracker(
                    title = "Afirmaciones",
                    icon = Icons.Filled.AutoAwesome,
                    streak = affirmationsStreak,
                )
                WeeklyStreakTracker(
                    title = "Meditación",
                    icon = Icons.Filled.Timer,
                    streak = meditationStreak,
                )
            }
        }

        item {
            Text(
                text = "Mis Afirmaciones",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            AddAffirmationCard(onAddAffirmation)
        }

        items(affirmations, key = { it.id }) { affirmation ->
            AffirmationRow(affirmation, onDeleteAffirmation)
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
                    text = "Racha: ${streak.streakDays} días",
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
                dayLabels.forEachIndexed { index, label ->
                    val completed = streak.completedDays.getOrElse(index) { false }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = if (completed) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = CircleShape
                            )
                            .then(
                                if (!completed) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
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

@Composable
private fun AddAffirmationCard(onAddAffirmation: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(swatches.first()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("Subtítulo") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                swatches.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { selectedColor = hex }
                            .background(color, CircleShape)
                            .border(
                                width = if (selectedColor == hex) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                    )
                }
            }
            Button(
                onClick = {
                    if (title.isNotBlank() && subtitle.isNotBlank()) {
                        onAddAffirmation(title.trim(), subtitle.trim(), selectedColor)
                        title = ""
                        subtitle = ""
                        selectedColor = swatches.first()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun AffirmationRow(affirmation: Affirmation, onDelete: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = affirmation.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onDelete(affirmation.id) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
