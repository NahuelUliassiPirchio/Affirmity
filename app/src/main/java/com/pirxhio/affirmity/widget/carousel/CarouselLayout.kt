package com.pirxhio.affirmity.widget.carousel

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The three responsive layouts of design 8. */
enum class CarouselSize { Small, Medium, Large }

/** Sizes registered with `SizeMode.Responsive`; Glance renders the largest one that fits. */
val CAROUSEL_RESPONSIVE_SIZES: Set<DpSize> = setOf(
    DpSize(110.dp, 110.dp),
    DpSize(250.dp, 110.dp),
    DpSize(250.dp, 250.dp),
)

private val MEDIUM_MIN_WIDTH = 250.dp
private val LARGE_MIN_HEIGHT = 250.dp

fun carouselSizeFor(width: Dp, height: Dp): CarouselSize = when {
    width >= MEDIUM_MIN_WIDTH && height >= LARGE_MIN_HEIGHT -> CarouselSize.Large
    width >= MEDIUM_MIN_WIDTH -> CarouselSize.Medium
    else -> CarouselSize.Small
}

/** Per-size metrics from design 8 (dp / sp). */
data class CarouselLayoutSpec(
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val titleSp: Float,
    val subtitleSp: Float,
    val titleMaxLines: Int,
    val subtitleMaxLines: Int,
    val markerAlpha: Float,
    val showDivider: Boolean,
    val showRefreshGlyph: Boolean,
)

fun carouselLayoutSpec(size: CarouselSize): CarouselLayoutSpec = when (size) {
    CarouselSize.Small -> CarouselLayoutSpec(
        paddingHorizontal = 14.dp, paddingVertical = 14.dp,
        titleSp = 19f, subtitleSp = 11.5f, titleMaxLines = 4, subtitleMaxLines = 2,
        markerAlpha = 0.24f, showDivider = false, showRefreshGlyph = false,
    )
    CarouselSize.Medium -> CarouselLayoutSpec(
        paddingHorizontal = 16.dp, paddingVertical = 15.dp,
        titleSp = 25f, subtitleSp = 13.5f, titleMaxLines = 3, subtitleMaxLines = 2,
        markerAlpha = 0.22f, showDivider = false, showRefreshGlyph = true,
    )
    CarouselSize.Large -> CarouselLayoutSpec(
        paddingHorizontal = 18.dp, paddingVertical = 18.dp,
        titleSp = 33f, subtitleSp = 16f, titleMaxLines = 6, subtitleMaxLines = Int.MAX_VALUE,
        markerAlpha = 0.18f, showDivider = true, showRefreshGlyph = false,
    )
}

/** Text after the leading star in the header; `null` means the star stands alone. */
fun headerLabel(size: CarouselSize, themeName: String?): String? {
    val name = themeName?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    return when (size) {
        CarouselSize.Small -> null
        CarouselSize.Medium -> name
        CarouselSize.Large -> if (name != null) "AFFIRMITY · $name" else "AFFIRMITY"
    }
}
