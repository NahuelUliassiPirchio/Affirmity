package com.pirxhio.affirmity.ui.affirmations

import android.widget.Toast
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.toArgb
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationBackground
import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.TemplateField
import com.pirxhio.affirmity.data.backgroundColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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

private data class FavoriteToggleIntent(
    val origin: Offset,
    val targetFavorite: Boolean,
)

@Composable
fun AffirmationsScreen(
    affirmations: List<Affirmation>,
    onAffirmationViewed: () -> Unit,
    onOverrideCommitted: (affirmationId: String, tokenKey: String, value: String) -> Unit = { _, _, _ -> },
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: (affirmationId: String) -> Unit = {},
    onHideAffirmation: (affirmationId: String) -> Unit = {},
    onAffirmationShared: (affirmationId: String) -> Unit = {},
    favoriteGesture: FavoriteGesture = FavoriteGesture.DOUBLE_TAP,
    isCleanScreen: Boolean = false,
    onCleanScreenChange: (Boolean) -> Unit = {},
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

    val pagerScope = rememberCoroutineScope()

    // Clean mode belongs to the feed, not to one card, so it survives swiping between pages.
    BackHandler(enabled = isCleanScreen) { onCleanScreenChange(false) }

    Box(modifier = Modifier.fillMaxSize()) {
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
            onHide = {
                onHideAffirmation(affirmation.id)
                // Advances the feed past the just-hidden card immediately, rather than leaving it
                // lingering until the hidden-ids DataStore flow catches up and re-filters the pool.
                pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            onShared = { onAffirmationShared(affirmation.id) },
            favoriteGesture = favoriteGesture,
            isCleanScreen = isCleanScreen,
            onEnterCleanScreen = { onCleanScreenChange(true) },
        )
    }
    if (isCleanScreen) {
        // Sits above the pager so it stays put while swiping; safeDrawing keeps it clear of the
        // display cutout and of the transient system bars that swipe back in.
        IconButton(
            onClick = { onCleanScreenChange(false) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.FullscreenExit,
                contentDescription = stringResource(R.string.affirmation_exit_clean_screen_content_description),
                tint = Color.White,
            )
        }
    }
    }
}

@Composable
private fun AffirmationCard(
    affirmation: Affirmation,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOverrideCommitted: (tokenKey: String, value: String) -> Unit,
    onHide: () -> Unit,
    onShared: () -> Unit,
    favoriteGesture: FavoriteGesture,
    isCleanScreen: Boolean,
    onEnterCleanScreen: () -> Unit,
) {
    var cardPositionInRoot by remember(affirmation.id) { mutableStateOf(Offset.Zero) }
    var cardSize by remember(affirmation.id) { mutableStateOf(IntSize.Zero) }
    var favoritePositionInRoot by remember(affirmation.id) { mutableStateOf(Offset.Zero) }
    var favoriteSize by remember(affirmation.id) { mutableStateOf(IntSize.Zero) }
    var previousIsFavorite by remember(affirmation.id) { mutableStateOf(isFavorite) }
    var pendingToggleIntent by remember(affirmation.id) { mutableStateOf<FavoriteToggleIntent?>(null) }
    val bursts = remember(affirmation.id) { mutableStateListOf<LikeBurst>() }
    val favoriteScale = remember(affirmation.id) { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun playLikeBurst(origin: Offset) {
        bursts += createLikeBurst(origin)
        favoriteScale.snapTo(1f)
        favoriteScale.animateTo(
            targetValue = 1.3f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        )
        favoriteScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        )
    }

    LaunchedEffect(isFavorite) {
        val favoriteChanged = previousIsFavorite != isFavorite
        previousIsFavorite = isFavorite
        if (!isFavorite) {
            if (pendingToggleIntent?.targetFavorite == false) {
                pendingToggleIntent = null
            }
            favoriteScale.snapTo(1f)
            return@LaunchedEffect
        }
        val intent = pendingToggleIntent
        if (favoriteChanged && intent?.targetFavorite == true) {
            pendingToggleIntent = null
            playLikeBurst(intent.origin)
        }
    }

    fun requestFavoriteToggle(origin: Offset) {
        val targetFavorite = pendingToggleIntent?.targetFavorite?.not() ?: !isFavorite
        pendingToggleIntent = FavoriteToggleIntent(origin = origin, targetFavorite = targetFavorite)
        onToggleFavorite()
    }

    /** Instagram-style double-tap: always shows the burst and never unlikes an already-liked card. */
    fun requestLikeBurst(origin: Offset) {
        if (isFavorite) {
            coroutineScope.launch { playLikeBurst(origin) }
        } else {
            requestFavoriteToggle(origin)
        }
    }

    val context = LocalContext.current
    val iconTint = MaterialTheme.colorScheme.primary.toArgb()
    val appName = stringResource(R.string.app_name)
    val shareErrorText = stringResource(R.string.affirmation_share_image_error)
    var showActions by remember(affirmation.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(affirmation.backgroundColor())
            .onGloballyPositioned { coordinates ->
                cardPositionInRoot = coordinates.positionInRoot()
                cardSize = coordinates.size
            }
            .pointerInput(affirmation.id, favoriteGesture) {
                detectTapGestures(
                    onDoubleTap = { offset -> requestLikeBurst(offset) },
                    onLongPress = { showActions = true },
                )
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
                    Brush.verticalGradient(colors = AffirmationScrimColors)
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
                modifier = if (isCleanScreen) {
                    // No boxed card: the text uses nearly the whole screen over the scrim.
                    Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(12.dp)
                        .imePadding()
                } else {
                    Modifier
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(24.dp)
                        .imePadding()
                },
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
                    onFavoriteToggleFromToken = {
                        requestLikeBurst(Offset(cardSize.width / 2f, cardSize.height / 2f))
                    },
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
                        onFavoriteToggleFromToken = {
                            requestFavoriteToggle(Offset(cardSize.width / 2f, cardSize.height / 2f))
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        LikeBurstOverlay(
            bursts = bursts,
            onBurstFinished = { burstId -> bursts.removeAll { it.id == burstId } },
            modifier = Modifier.fillMaxSize(),
        )
        if (!isCleanScreen) IconButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .onGloballyPositioned { coordinates ->
                    favoritePositionInRoot = coordinates.positionInRoot()
                    favoriteSize = coordinates.size
                }
                .graphicsLayer {
                    scaleX = favoriteScale.value
                    scaleY = favoriteScale.value
                },
            onClick = {
                requestFavoriteToggle(
                    favoritePositionInRoot - cardPositionInRoot + Offset(
                        favoriteSize.width / 2f,
                        favoriteSize.height / 2f,
                    )
                )
            },
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = Color.White,
            )
        }
    }

    // Long press, not a corner icon row: MainActivity draws FloatingStatusOverlay (streak + avatar)
    // over this screen at Alignment.TopEnd, which silently covered an earlier always-visible
    // share/hide row placed in that same corner. A gesture also keeps the card uncluttered, and
    // matches the double-tap-to-favorite gesture this screen already teaches.
    if (showActions) {
        AffirmationActionsSheet(
            onShareImage = {
                showActions = false
                coroutineScope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching {
                            val target = ShareImageCache(File(context.cacheDir, ShareImageCache.SUBDIR))
                                .prepare(affirmation.id)
                            renderAffirmationShareImage(
                                context, affirmation, affirmation.icon(), iconTint, appName, target,
                            )
                            target
                        }.getOrNull()
                    }
                    val started = file != null && runCatching {
                        context.startActivity(buildShareImageIntent(context, file))
                    }.isSuccess
                    if (started) onShared() else Toast.makeText(context, shareErrorText, Toast.LENGTH_SHORT).show()
                }
            },
            onHide = {
                showActions = false
                onHide()
            },
            onCleanScreen = {
                showActions = false
                onEnterCleanScreen()
            },
            onDismiss = { showActions = false },
        )
    }
}

/** Share-as-image/hide/clean-screen actions for the affirmation under a long press. Favouriting is deliberately absent:
 *  it already has two affordances on the card itself (the heart, and double-tap). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AffirmationActionsSheet(
    onShareImage: () -> Unit,
    onHide: () -> Unit,
    onCleanScreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            AffirmationActionRow(
                icon = Icons.Filled.Image,
                label = stringResource(R.string.affirmation_share_image_content_description),
                onClick = onShareImage,
            )
            AffirmationActionRow(
                icon = Icons.Filled.VisibilityOff,
                label = stringResource(R.string.affirmation_hide_content_description),
                onClick = onHide,
            )
            AffirmationActionRow(
                icon = Icons.Filled.Fullscreen,
                label = stringResource(R.string.affirmation_clean_screen_content_description),
                onClick = onCleanScreen,
            )
        }
    }
}

@Composable
private fun AffirmationActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 18.dp),
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
internal fun Affirmation.icon(): ImageVector {
    val icons = listOf(
        Icons.Filled.SelfImprovement,
        Icons.Filled.WaterDrop,
        Icons.Filled.Favorite,
        Icons.Filled.AutoAwesome,
    )
    val index = (id.hashCode().mod(icons.size) + icons.size) % icons.size
    return icons[index]
}
