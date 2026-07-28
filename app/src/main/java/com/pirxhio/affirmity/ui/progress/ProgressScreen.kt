package com.pirxhio.affirmity.ui.progress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
    addImageError: String?,
    onAddAffirmationWithColor: (title: String, subtitle: String, colorHex: String) -> Unit,
    onAddAffirmationWithImage: (title: String, subtitle: String, imageUrl: String) -> Unit,
    onAddAffirmationWithGalleryImage: (title: String, subtitle: String, imageUri: Uri) -> Unit,
    onDeleteAffirmation: (id: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Mi Progreso",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Ajustes"
                    )
                }
            }
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
            AddAffirmationCard(
                downloadError = addImageError,
                onAddAffirmationWithColor = onAddAffirmationWithColor,
                onAddAffirmationWithImage = onAddAffirmationWithImage,
                onAddAffirmationWithGalleryImage = onAddAffirmationWithGalleryImage,
            )
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

private enum class BackgroundMode { COLOR, IMAGE_URL, GALLERY }

@Composable
private fun AddAffirmationCard(
    downloadError: String?,
    onAddAffirmationWithColor: (String, String, String) -> Unit,
    onAddAffirmationWithImage: (String, String, String) -> Unit,
    onAddAffirmationWithGalleryImage: (String, String, Uri) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(swatches.first()) }
    var backgroundMode by remember { mutableStateOf(BackgroundMode.COLOR) }
    var imageUrl by remember { mutableStateOf("") }
    var galleryUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> galleryUri = uri }
    )

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
                FilterChip(
                    selected = backgroundMode == BackgroundMode.COLOR,
                    onClick = { backgroundMode = BackgroundMode.COLOR },
                    label = { Text("Color") }
                )
                FilterChip(
                    selected = backgroundMode == BackgroundMode.IMAGE_URL,
                    onClick = { backgroundMode = BackgroundMode.IMAGE_URL },
                    label = { Text("URL") }
                )
                FilterChip(
                    selected = backgroundMode == BackgroundMode.GALLERY,
                    onClick = { backgroundMode = BackgroundMode.GALLERY },
                    label = { Text("Galería") }
                )
            }
            when (backgroundMode) {
                BackgroundMode.COLOR -> {
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
                }

                BackgroundMode.IMAGE_URL -> {
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("URL de la imagen") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                BackgroundMode.GALLERY -> {
                    Button(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (galleryUri == null) "Elegir de la galería" else "Cambiar imagen elegida")
                    }
                }
            }
            if (downloadError != null && backgroundMode != BackgroundMode.COLOR) {
                Text(
                    text = downloadError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    if (title.isBlank() || subtitle.isBlank()) return@Button
                    when (backgroundMode) {
                        BackgroundMode.COLOR -> {
                            onAddAffirmationWithColor(title.trim(), subtitle.trim(), selectedColor)
                            selectedColor = swatches.first()
                        }
                        BackgroundMode.IMAGE_URL -> {
                            if (imageUrl.isBlank()) return@Button
                            onAddAffirmationWithImage(title.trim(), subtitle.trim(), imageUrl.trim())
                            imageUrl = ""
                        }
                        BackgroundMode.GALLERY -> {
                            val uri = galleryUri ?: return@Button
                            onAddAffirmationWithGalleryImage(title.trim(), subtitle.trim(), uri)
                            galleryUri = null
                        }
                    }
                    title = ""
                    subtitle = ""
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
