package com.pirxhio.affirmity.ui.affirmations

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationBackground
import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.TemplateField
import com.pirxhio.affirmity.data.backgroundColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

/**
 * Full-screen, swipeable card feed matching afirmaciones.html: one card per page,
 * solid-color background (no image download in this pass) with a dark gradient
 * scrim, icon + serif title + divider + body subtitle.
 *
 * Tradeoff: VerticalPager doesn't support true infinite looping out of the box.
 * We approximate the mockup's "loop back to start" behavior by wrapping the swipe
 * index with modulo arithmetic against a very large virtual page count, rather than
 * a strict non-looping pager.
 */
private const val LOOP_MULTIPLIER = 10_000

@Composable
fun AffirmationsScreen(
    affirmations: List<Affirmation>,
    onAffirmationViewed: () -> Unit,
    onOverrideCommitted: (affirmationId: String, tokenKey: String, value: String) -> Unit = { _, _, _ -> },
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: (affirmationId: String) -> Unit = {},
    favoriteGesture: FavoriteGesture = FavoriteGesture.DOUBLE_TAP,
) {
    if (affirmations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Agrega tu primera afirmación desde Progreso.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    val virtualPageCount = affirmations.size * LOOP_MULTIPLIER
    val startPage = virtualPageCount / 2 - (virtualPageCount / 2) % affirmations.size
    val pagerState: PagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { virtualPageCount }
    )

    // Counts as "viewed" once the swipe settles on a new page, matching what a user
    // would perceive as having actually read that affirmation (vs. a mid-swipe frame).
    // drop(1): snapshotFlow emits the current settledPage immediately on collection, before any
    // swipe happens -- without dropping it, just mounting this screen (e.g. reopening the app)
    // counts as a view.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { onAffirmationViewed() }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val affirmation = affirmations[page % affirmations.size]
        AffirmationCard(
            affirmation = affirmation,
            isFavorite = affirmation.id in favoriteIds,
            onToggleFavorite = { onToggleFavorite(affirmation.id) },
            onOverrideCommitted = { tokenKey, value -> onOverrideCommitted(affirmation.id, tokenKey, value) },
            favoriteGesture = favoriteGesture,
        )
    }
}

@Composable
private fun AffirmationCard(
    affirmation: Affirmation,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOverrideCommitted: (tokenKey: String, value: String) -> Unit,
    favoriteGesture: FavoriteGesture,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(affirmation.backgroundColor())
            .pointerInput(affirmation.id, favoriteGesture) {
                detectTapGestures(onDoubleTap = { onToggleFavorite() })
            }
    ) {
        val background = affirmation.background
        if (background is AffirmationBackground.Image) {
            AffirmationImageBackground(background.localPath)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.8f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val tokenStyle = defaultTokenStyle
            val titleTemplate = remember(affirmation.title) {
                AffirmationTemplateParser.parse(TemplateField.TITLE, affirmation.title)
            }
            val subtitleTemplate = remember(affirmation.subtitle) {
                AffirmationTemplateParser.parse(TemplateField.SUBTITLE, affirmation.subtitle)
            }
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = affirmation.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(32.dp)
                )
                TokenizedAffirmationText(
                    template = titleTemplate,
                    overrides = affirmation.overrides,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    tokenStyle = tokenStyle,
                    editable = true,
                    onOverrideCommitted = onOverrideCommitted,
                    favoriteTapEnabled = true,
                    onFavoriteToggleFromToken = onToggleFavorite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (affirmation.subtitle.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .width(48.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    )
                    TokenizedAffirmationText(
                        template = subtitleTemplate,
                        overrides = affirmation.overrides,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCCCCCC),
                        tokenStyle = tokenStyle,
                        editable = true,
                        onOverrideCommitted = onOverrideCommitted,
                        favoriteTapEnabled = true,
                        onFavoriteToggleFromToken = onToggleFavorite,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
        )
    }
}

@Composable
private fun AffirmationImageBackground(localPath: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, localPath) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(localPath)?.asImageBitmap() }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/** Stable pseudo-random icon per affirmation, matching the variety used in the mockup cards. */
private fun Affirmation.icon(): ImageVector {
    val icons = listOf(
        Icons.Filled.SelfImprovement,
        Icons.Filled.WaterDrop,
        Icons.Filled.Favorite,
        Icons.Filled.AutoAwesome,
    )
    val index = (id.hashCode().mod(icons.size) + icons.size) % icons.size
    return icons[index]
}
