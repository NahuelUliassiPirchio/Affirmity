package com.pirxhio.affirmity.widget.carousel

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.R

/** Extras understood by `MainActivity` to open on the affirmations tab (same as the tracker widget). */
private const val EXTRA_START_DESTINATION = "start_destination"
private const val HOME_DESTINATION = "AFIRMACIONES"

private val TEXT_COLOR = Color(0xFFFFFFFF)
private val SUBTITLE_COLOR = Color(0xCCFFFFFF)
private val NOTICE_COLOR = Color(0x99FFFFFF)

/** The localized notice line for a fallback reason (R5.5). */
fun noticeStringRes(reason: FallbackReason): Int = when (reason) {
    FallbackReason.SOURCE_MISSING -> R.string.carousel_notice_source_missing
    FallbackReason.SOURCE_EMPTY -> R.string.carousel_notice_source_empty
    FallbackReason.SOURCE_LOCKED -> R.string.carousel_notice_source_locked
    FallbackReason.DATA_UNAVAILABLE -> R.string.carousel_notice_data_unavailable
}

/**
 * Pure content composable (R11.2): renders a precomputed [frame], no I/O. A `null` frame shows the
 * localized placeholder. The whole body is one tap target that opens the app (R9); there are
 * intentionally no previous/next controls.
 */
@Composable
internal fun CarouselContent(frame: CarouselFrame?) {
    val context = LocalContext.current
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_DESTINATION, HOME_DESTINATION)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_bg))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity(tapIntent)),
        contentAlignment = Alignment.Center,
    ) {
        if (frame == null) {
            Text(
                text = context.getString(R.string.carousel_placeholder),
                style = TextStyle(fontSize = 14.sp, color = ColorProvider(TEXT_COLOR), textAlign = TextAlign.Center),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = frame.text,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorProvider(TEXT_COLOR),
                        textAlign = TextAlign.Center,
                    ),
                )
                frame.subtitle?.let {
                    Text(
                        text = it,
                        modifier = GlanceModifier.padding(top = 4.dp),
                        style = TextStyle(fontSize = 12.sp, color = ColorProvider(SUBTITLE_COLOR), textAlign = TextAlign.Center),
                    )
                }
                frame.notice?.let {
                    Text(
                        text = context.getString(noticeStringRes(it)),
                        modifier = GlanceModifier.padding(top = 6.dp),
                        style = TextStyle(fontSize = 10.sp, color = ColorProvider(NOTICE_COLOR), textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}
