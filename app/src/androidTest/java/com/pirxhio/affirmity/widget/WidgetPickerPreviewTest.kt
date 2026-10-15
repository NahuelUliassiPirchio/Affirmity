package com.pirxhio.affirmity.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.pirxhio.affirmity.widget.carousel.AffirmationCarouselReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the widget picker entries: both providers declare a preview layout, a fallback image and a description. */
@SdkSuppress(minSdkVersion = 31) // previewLayout is API 31+
class WidgetPickerPreviewTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun providerFor(receiver: Class<*>): AppWidgetProviderInfo {
        val matches = AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(context.packageName, null)
            .filter { it.provider.className == receiver.name }
        assertEquals("Expected exactly one provider for ${receiver.name}", 1, matches.size)
        return matches.single()
    }

    private fun assertPickerEntry(info: AppWidgetProviderInfo) {
        assertTrue("previewLayout missing", info.previewLayout != 0)
        assertTrue("previewImage missing", info.previewImage != 0)
        assertTrue("description missing", !info.loadDescription(context).isNullOrBlank())

        // Launchers below API 31 size the fallback from its intrinsic dimensions; -1 renders nothing.
        val fallback = requireNotNull(info.loadPreviewImage(context, 0)) { "previewImage did not load" }
        assertTrue("previewImage has no intrinsic size", fallback.intrinsicWidth > 0 && fallback.intrinsicHeight > 0)
        assertFallbackLayersAreSeparate(fallback)

        var failure: Throwable? = null
        var root: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            failure = runCatching {
                root = RemoteViews(context.packageName, info.previewLayout).apply(context, FrameLayout(context))
            }.exceptionOrNull()
        }
        failure?.let { throw AssertionError("Preview layout is not RemoteViews-compatible", it) }

        // XML android:tint defaults to SRC_ATOP, which ignores the tint's alpha on opaque vectors.
        val tinted = tintedImages(root!!)
        assertTrue("Expected tinted images in the preview", tinted.isNotEmpty())
        tinted.forEach { assertEquals("Tint must honor alpha", BlendMode.SRC_IN, it.imageTintBlendMode) }
    }

    /** Layer 0 is the card; every other layer must sit inside it without overlapping its siblings. */
    private fun assertFallbackLayersAreSeparate(fallback: Drawable) {
        val layers = fallback as? LayerDrawable ?: return
        val canvas = Rect(0, 0, layers.intrinsicWidth, layers.intrinsicHeight)
        layers.bounds = canvas
        val shapes = (1 until layers.numberOfLayers).map { Rect(layers.getDrawable(it).bounds) }
        shapes.forEachIndexed { i, shape ->
            assertTrue("Layer ${i + 1} $shape is empty or outside $canvas", !shape.isEmpty && canvas.contains(shape))
            shapes.drop(i + 1).forEach { other ->
                assertTrue("Layers overlap: $shape and $other", !Rect.intersects(shape, other))
            }
        }
    }

    private fun tintedImages(view: View): List<ImageView> = when (view) {
        is ImageView -> listOfNotNull(view.takeIf { it.imageTintList != null })
        is ViewGroup -> (0 until view.childCount).flatMap { tintedImages(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun weeklyTracker_declaresCompletePickerEntry() = assertPickerEntry(providerFor(WeeklyTrackerWidgetReceiver::class.java))

    @Test
    fun carousel_declaresCompletePickerEntry() = assertPickerEntry(providerFor(AffirmationCarouselReceiver::class.java))
}
