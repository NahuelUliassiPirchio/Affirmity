package com.pirxhio.affirmity.ui.myaffirmations

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation

private val swatches = listOf(
    "#2A9D8F", "#00696F", "#5BBCC3", "#5E5E5E", "#8F4D22", "#BA1A1A"
)

/** Extracted from `ProgressScreen` — the add-affirmation form plus the full list of the user's
 * own affirmations, now its own screen reached from the group selector's "+" card. */
@Composable
fun MyAffirmationsScreen(
    affirmations: List<Affirmation>,
    addImageError: String?,
    importError: String?,
    onAddAffirmationWithColor: (title: String, subtitle: String, colorHex: String) -> Unit,
    onAddAffirmationWithImage: (title: String, subtitle: String, imageUrl: String) -> Unit,
    onAddAffirmationWithGalleryImage: (title: String, subtitle: String, imageUri: Uri) -> Unit,
    onImportAffirmationsJson: (json: String, replaceExisting: Boolean) -> Unit,
    onDeleteAffirmation: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
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
    var showValidationError by remember { mutableStateOf(false) }
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
                    onValueChange = { title = it; showValidationError = false },
                    label = { Text(stringResource(R.string.progress_title_field_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showValidationError && title.isBlank(),
                    supportingText = if (showValidationError && title.isBlank()) {
                        { Text(stringResource(R.string.progress_title_field_error)) }
                    } else null,
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text(stringResource(R.string.progress_subtitle_field_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = backgroundMode == BackgroundMode.COLOR,
                    onClick = { backgroundMode = BackgroundMode.COLOR },
                    label = { Text(stringResource(R.string.progress_mode_color_label)) }
                )
                FilterChip(
                    selected = backgroundMode == BackgroundMode.IMAGE_URL,
                    onClick = { backgroundMode = BackgroundMode.IMAGE_URL },
                    label = { Text(stringResource(R.string.progress_mode_url_label)) }
                )
                FilterChip(
                    selected = backgroundMode == BackgroundMode.GALLERY,
                    onClick = { backgroundMode = BackgroundMode.GALLERY },
                    label = { Text(stringResource(R.string.progress_mode_gallery_label)) }
                )
                FilterChip(
                    selected = backgroundMode == BackgroundMode.JSON_IMPORT,
                    onClick = { backgroundMode = BackgroundMode.JSON_IMPORT },
                    label = { Text(stringResource(R.string.progress_mode_json_label)) }
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
                        onValueChange = { imageUrl = it; showValidationError = false },
                        label = { Text(stringResource(R.string.progress_image_url_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = showValidationError && imageUrl.isBlank(),
                        supportingText = if (showValidationError && imageUrl.isBlank()) {
                            { Text(stringResource(R.string.progress_image_url_error)) }
                        } else null,
                    )
                }

                BackgroundMode.GALLERY -> {
                    Button(
                        onClick = {
                            showValidationError = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (galleryUri == null) {
                                stringResource(R.string.progress_gallery_pick_button)
                            } else {
                                stringResource(R.string.progress_gallery_change_button)
                            }
                        )
                    }
                    if (showValidationError && galleryUri == null) {
                        Text(
                            text = stringResource(R.string.progress_gallery_error),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
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
                        Text(stringResource(R.string.progress_copy_json_example_button))
                    }
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        label = { Text(stringResource(R.string.progress_json_paste_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.progress_replace_existing_label),
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
                            if (title.isBlank()) {
                                showValidationError = true
                                return@Button
                            }
                            onAddAffirmationWithColor(title.trim(), subtitle.trim(), selectedColor)
                            selectedColor = swatches.first()
                            title = ""
                            subtitle = ""
                        }
                        BackgroundMode.IMAGE_URL -> {
                            if (title.isBlank() || imageUrl.isBlank()) {
                                showValidationError = true
                                return@Button
                            }
                            onAddAffirmationWithImage(title.trim(), subtitle.trim(), imageUrl.trim())
                            imageUrl = ""
                            title = ""
                            subtitle = ""
                        }
                        BackgroundMode.GALLERY -> {
                            val uri = galleryUri
                            if (title.isBlank() || uri == null) {
                                showValidationError = true
                                return@Button
                            }
                            onAddAffirmationWithGalleryImage(title.trim(), subtitle.trim(), uri)
                            galleryUri = null
                            title = ""
                            subtitle = ""
                        }
                        BackgroundMode.JSON_IMPORT -> {
                            if (importJson.isBlank()) {
                                showValidationError = true
                                return@Button
                            }
                            onImportAffirmationsJson(importJson, replaceExisting)
                            importJson = ""
                        }
                    }
                    showValidationError = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (backgroundMode == BackgroundMode.JSON_IMPORT) {
                        stringResource(R.string.progress_import_button)
                    } else {
                        stringResource(R.string.progress_save_button)
                    }
                )
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
                    contentDescription = stringResource(R.string.progress_delete_content_description),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
