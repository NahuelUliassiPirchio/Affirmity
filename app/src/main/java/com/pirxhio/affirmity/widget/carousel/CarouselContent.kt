package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.unit.ColorProvider
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.R

/** Extras understood by `MainActivity` to open on the affirmations tab (same as the tracker widget). */
private const val EXTRA_START_DESTINATION = "start_destination"
private const val HOME_DESTINATION = "AFIRMACIONES"

/** Design 8 palette as (light, dark) pairs, resolved by the system day/night mode. */
private object Palette {
    fun pair(light: Long, dark: Long) = dayNightColorProvider(day = Color(light), night = Color(dark))

    val star = pair(0xFF2E7F86, 0xFF5BBCC3)
    val header = pair(0xFF6A6E76, 0xFF9AA0A8)
    val glyph = pair(0xFF6A6E76, 0xFF6B7076)
    val title = pair(0xFF1B1D22, 0xFFF2F3F5)
    val subtitle = pair(0xFF50545C, 0xFFB4B9C0)
    val divider = dayNightColorProvider(day = Color(0x8C2E7F86), night = Color(0x8C5BBCC3))
    val notice = pair(0xFF6A6E76, 0xFF9AA0A8)
}

private const val STAR = "\u2726"
private const val REFRESH_GLYPH = "\u21BB"

/** The localized notice line for a fallback reason (R5.5). */
fun noticeStringRes(reason: FallbackReason): Int = when (reason) {
    FallbackReason.SOURCE_MISSING -> R.string.carousel_notice_source_missing
    FallbackReason.SOURCE_EMPTY -> R.string.carousel_notice_source_empty
    FallbackReason.SOURCE_LOCKED -> R.string.carousel_notice_source_locked
    FallbackReason.DATA_UNAVAILABLE -> R.string.carousel_notice_data_unavailable
}

/**
 * Pure content composable (R11.2): renders a precomputed [frame], no I/O. A `null` frame shows the
 * localized placeholder. The whole card is one tap target that opens the app (R9); there are
 * intentionally no previous/next controls. Layout (small, medium, large) follows [size], the
 * responsive size Glance is currently rendering.
 */
@Composable
internal fun CarouselContent(
    frame: CarouselFrame?,
    theme: CarouselTheme = CarouselTheme.Default,
    size: DpSize = LocalSize.current,
) {
    val context = LocalContext.current
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_DESTINATION, HOME_DESTINATION)
    }
    val cardSize = carouselSizeFor(size.width, size.height)
    val spec = carouselLayoutSpec(cardSize)

    // `cornerRadius` clips the children (and so the marker) to the card, but only exists on API 31+;
    // the rounded drawable background covers the shape everywhere else.
    val clipsToCorners = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var card = GlanceModifier
        .fillMaxSize()
        .background(ImageProvider(R.drawable.widget_carousel_card))
    if (clipsToCorners) card = card.cornerRadius(22.dp)
    card = card.clickable(actionStartActivity(tapIntent))

    Box(modifier = card) {
        // Below API 31 the marker would paint over the rounded corner, so it is skipped there.
        if (clipsToCorners) {
            Image(
                provider = ImageProvider(markerRes(cardSize)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(ColorProvider(theme.highlight.copy(alpha = spec.markerAlpha))),
            )
        }
        if (frame == null) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(spec.paddingHorizontal), contentAlignment = Alignment.Center) {
                Text(
                    text = context.getString(R.string.carousel_placeholder),
                    style = TextStyle(fontSize = 13.sp, color = Palette.subtitle, textAlign = TextAlign.Center),
                )
            }
        } else {
            Frame(frame, theme, cardSize, spec, context)
        }
    }
}

private fun markerRes(size: CarouselSize): Int = when (size) {
    CarouselSize.Small -> R.drawable.widget_carousel_marker_small
    CarouselSize.Medium -> R.drawable.widget_carousel_marker_medium
    CarouselSize.Large -> R.drawable.widget_carousel_marker_large
}

@Composable
private fun Frame(
    frame: CarouselFrame,
    theme: CarouselTheme,
    size: CarouselSize,
    spec: CarouselLayoutSpec,
    context: Context,
) {
    val themeName = when (val title = theme.title) {
        is SourceTitle.Res -> context.getString(title.id)
        is SourceTitle.Text -> title.value
        null -> null
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = spec.paddingHorizontal, vertical = spec.paddingVertical),
    ) {
        Header(size, spec, headerLabel(size, themeName))
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = frame.text,
            maxLines = spec.titleMaxLines,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = spec.titleSp.sp,
                color = Palette.title,
            ),
        )
        if (spec.showDivider && frame.subtitle != null) {
            Spacer(GlanceModifier.height(16.dp))
            Box(modifier = GlanceModifier.width(40.dp).height(1.dp).background(Palette.divider)) {}
            Spacer(GlanceModifier.height(14.dp))
        }
        frame.subtitle?.let {
            Text(
                text = it,
                maxLines = spec.subtitleMaxLines,
                modifier = if (spec.showDivider) GlanceModifier else GlanceModifier.padding(top = 5.dp),
                style = TextStyle(fontSize = spec.subtitleSp.sp, color = Palette.subtitle),
            )
        }
        frame.notice?.let {
            Text(
                text = context.getString(noticeStringRes(it)),
                maxLines = 2,
                modifier = GlanceModifier.padding(top = 6.dp),
                style = TextStyle(fontSize = 10.sp, color = Palette.notice),
            )
        }
    }
}

@Composable
private fun Header(size: CarouselSize, spec: CarouselLayoutSpec, label: String?) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = STAR, style = TextStyle(fontSize = 11.sp, color = Palette.star))
        label?.let {
            Spacer(GlanceModifier.width(6.dp))
            // Glance 1.1 has no letter spacing, so the design's 0.6px tracking is not reproduced.
            Text(text = it, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = Palette.header))
        }
        if (spec.showRefreshGlyph) {
            Spacer(GlanceModifier.defaultWeight())
            Text(text = REFRESH_GLYPH, style = TextStyle(fontSize = 13.sp, color = Palette.glyph))
        }
    }
}
