package com.pirxhio.affirmity.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.TemplateField
import com.pirxhio.affirmity.ui.affirmations.TokenizedAffirmationText
import com.pirxhio.affirmity.ui.affirmations.defaultTokenStyle

@Composable
fun FavoritesScreen(
    favorites: List<Affirmation>,
    onUnfavorite: (affirmationId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.favorites_empty_state),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(favorites, key = { it.id }) { affirmation ->
            FavoriteAffirmationRow(
                affirmation = affirmation,
                onUnfavorite = { onUnfavorite(affirmation.id) },
            )
        }
    }
}

@Composable
private fun FavoriteAffirmationRow(
    affirmation: Affirmation,
    onUnfavorite: () -> Unit,
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
            IconButton(onClick = onUnfavorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(
                        R.string.favorites_unlike_content_description,
                    ),
                )
            }
        }
    }
}
