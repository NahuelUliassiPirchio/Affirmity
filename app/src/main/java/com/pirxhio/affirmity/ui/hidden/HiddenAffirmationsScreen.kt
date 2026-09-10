package com.pirxhio.affirmity.ui.hidden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.TemplateField
import com.pirxhio.affirmity.ui.affirmations.TokenizedAffirmationText
import com.pirxhio.affirmity.ui.affirmations.defaultTokenStyle

/** "Manage hidden affirmations" screen (pre-launch audit item #1): lists every catalog
 *  affirmation the user hid from their rotation, resolved the same way [hiddenAffirmations]
 *  resolves them in [com.pirxhio.affirmity.data.AffirmityAppState] -- mirrors
 *  [com.pirxhio.affirmity.ui.favorites.FavoritesScreen]'s layout/empty-state pattern. */
@Composable
fun HiddenAffirmationsScreen(
    hidden: List<Affirmation>,
    onUnhide: (affirmationId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hidden.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    text = stringResource(R.string.hidden_affirmations_empty_state),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(hidden, key = { it.id }) { affirmation ->
            HiddenAffirmationRow(
                affirmation = affirmation,
                onUnhide = { onUnhide(affirmation.id) },
            )
        }
    }
}

@Composable
private fun HiddenAffirmationRow(
    affirmation: Affirmation,
    onUnhide: () -> Unit,
) {
    val titleTemplate = remember(affirmation.title) {
        AffirmationTemplateParser.parse(TemplateField.TITLE, affirmation.title)
    }
    val subtitleTemplate = remember(affirmation.subtitle) {
        AffirmationTemplateParser.parse(TemplateField.SUBTITLE, affirmation.subtitle)
    }
    val tokenStyle = defaultTokenStyle

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TokenizedAffirmationText(
                    template = titleTemplate,
                    overrides = affirmation.overrides,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    tokenStyle = tokenStyle,
                    editable = false,
                    onOverrideCommitted = { _, _ -> },
                )
                if (affirmation.subtitle.isNotBlank()) {
                    TokenizedAffirmationText(
                        template = subtitleTemplate,
                        overrides = affirmation.overrides,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        tokenStyle = tokenStyle,
                        editable = false,
                        onOverrideCommitted = { _, _ -> },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onUnhide) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(
                        R.string.hidden_affirmations_unhide_content_description,
                    ),
                )
            }
        }
    }
}
