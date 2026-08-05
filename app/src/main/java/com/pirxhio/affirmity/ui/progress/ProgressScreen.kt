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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.HealerActivation
import com.pirxhio.affirmity.data.StreakHealerState
import com.pirxhio.affirmity.data.WeeklyStreak

private val swatches = listOf(
    "#2A9D8F", "#00696F", "#5BBCC3", "#5E5E5E", "#8F4D22", "#BA1A1A"
)

@Composable
fun ProgressScreen(
    affirmations: List<Affirmation>,
    affirmationsStreak: WeeklyStreak,
    meditationStreak: WeeklyStreak,
    streakHealer: StreakHealerState,
    addImageError: String?,
    importError: String?,
    onAddAffirmationWithColor: (title: String, subtitle: String, colorHex: String) -> Unit,
    onAddAffirmationWithImage: (title: String, subtitle: String, imageUrl: String) -> Unit,
    onAddAffirmationWithGalleryImage: (title: String, subtitle: String, imageUri: Uri) -> Unit,
    onImportAffirmationsJson: (json: String, replaceExisting: Boolean) -> Unit,
    onDeleteAffirmation: (id: String) -> Unit,
    onOpenSettings: () -> Unit,
    onActivateHealer: () -> Unit,
    profilePhotoUrl: String? = null,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Mi Progreso",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ProfileAvatar(photoUrl = profilePhotoUrl, onClick = onOpenSettings)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Hábitos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Always visible regardless of healer/activation state — distinct from the
                // StreakHealerCard below (spec's `general-streak` domain: alive if either habit
                // was done that day, independent of the per-habit trackers further down).
                GeneralStreakCounter(generalStreakDays = streakHealer.generalStreakDays)
                StreakHealerCard(streakHealer = streakHealer, onActivateHealer = onActivateHealer)
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
                importError = importError,
                onAddAffirmationWithColor = onAddAffirmationWithColor,
                onAddAffirmationWithImage = onAddAffirmationWithImage,
                onAddAffirmationWithGalleryImage = onAddAffirmationWithGalleryImage,
                onImportAffirmationsJson = onImportAffirmationsJson,
            )
        }

        items(affirmations, key = { it.id }) { affirmation ->
            AffirmationRow(affirmation, onDeleteAffirmation)
        }
    }
}

@Composable
private fun ProfileAvatar(photoUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Perfil y ajustes",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Perfil y ajustes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Always-visible general-streak day counter (spec's `general-streak` domain) — separate from the
 * per-habit trackers and from [StreakHealerCard]'s own held/CTA state. */
@Composable
private fun GeneralStreakCounter(generalStreakDays: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Racha general: $generalStreakDays días",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
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
                    text = "Sanador de racha",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            when (activation) {
                is HealerActivation.Available -> {
                    Text(
                        text = "Ayer no completaste ningún hábito. Activá el sanador para conservar tu racha " +
                            "general — esto no reemplaza lo que hagas hoy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onActivateHealer, modifier = Modifier.fillMaxWidth()) {
                        Text("Activar sanador")
                    }
                }

                is HealerActivation.UsedToday -> {
                    Text(
                        text = "Sanador activado: tu racha general sigue intacta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HealerActivation.Unavailable -> {
                    // streakHealer.healerHeld is true here (guarded above): show the held badge.
                    Text(
                        text = "Tenés 1 sanador guardado para el próximo día que falles.",
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
                streak.dayLabels.forEachIndexed { index, label ->
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

private enum class BackgroundMode { COLOR, IMAGE_URL, GALLERY, JSON_IMPORT }

/** Shape reference for [onImportAffirmationsJson] — handy to paste into an LLM prompt. */
private const val AFFIRMATIONS_JSON_EXAMPLE = """[
  {
    "title": "I am capable of change",
    "subtitle": "Growth starts with a single choice",
    "background": {
      "type": "color",
      "value": "#2A9D8F"
    }
  }
]"""

@Composable
private fun AddAffirmationCard(
    downloadError: String?,
    importError: String?,
    onAddAffirmationWithColor: (String, String, String) -> Unit,
    onAddAffirmationWithImage: (String, String, String) -> Unit,
    onAddAffirmationWithGalleryImage: (String, String, Uri) -> Unit,
    onImportAffirmationsJson: (json: String, replaceExisting: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(swatches.first()) }
    var backgroundMode by remember { mutableStateOf(BackgroundMode.COLOR) }
    var imageUrl by remember { mutableStateOf("") }
    var galleryUri by remember { mutableStateOf<Uri?>(null) }
    var importJson by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

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
            if (backgroundMode != BackgroundMode.JSON_IMPORT) {
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
            }
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
                FilterChip(
                    selected = backgroundMode == BackgroundMode.JSON_IMPORT,
                    onClick = { backgroundMode = BackgroundMode.JSON_IMPORT },
                    label = { Text("JSON") }
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

                BackgroundMode.JSON_IMPORT -> {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(AFFIRMATIONS_JSON_EXAMPLE))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Text(" Copiar ejemplo de JSON")
                    }
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        label = { Text("Pegar JSON de afirmaciones") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Reemplazar afirmaciones actuales",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                    }
                }
            }
            if (downloadError != null && backgroundMode != BackgroundMode.COLOR && backgroundMode != BackgroundMode.JSON_IMPORT) {
                Text(
                    text = downloadError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (importError != null && backgroundMode == BackgroundMode.JSON_IMPORT) {
                Text(
                    text = importError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    when (backgroundMode) {
                        BackgroundMode.COLOR -> {
                            if (title.isBlank() || subtitle.isBlank()) return@Button
                            onAddAffirmationWithColor(title.trim(), subtitle.trim(), selectedColor)
                            selectedColor = swatches.first()
                            title = ""
                            subtitle = ""
                        }
                        BackgroundMode.IMAGE_URL -> {
                            if (title.isBlank() || subtitle.isBlank() || imageUrl.isBlank()) return@Button
                            onAddAffirmationWithImage(title.trim(), subtitle.trim(), imageUrl.trim())
                            imageUrl = ""
                            title = ""
                            subtitle = ""
                        }
                        BackgroundMode.GALLERY -> {
                            if (title.isBlank() || subtitle.isBlank()) return@Button
                            val uri = galleryUri ?: return@Button
                            onAddAffirmationWithGalleryImage(title.trim(), subtitle.trim(), uri)
                            galleryUri = null
                            title = ""
                            subtitle = ""
                        }
                        BackgroundMode.JSON_IMPORT -> {
                            if (importJson.isBlank()) return@Button
                            onImportAffirmationsJson(importJson, replaceExisting)
                            importJson = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (backgroundMode == BackgroundMode.JSON_IMPORT) "Importar" else "Guardar")
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
