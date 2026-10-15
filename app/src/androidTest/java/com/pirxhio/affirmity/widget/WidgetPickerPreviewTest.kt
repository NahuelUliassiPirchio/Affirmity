package com.pirxhio.affirmity.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.FrameLayout
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

        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            failure = runCatching {
                RemoteViews(context.packageName, info.previewLayout).apply(context, FrameLayout(context))
            }.exceptionOrNull()
        }
        failure?.let { throw AssertionError("Preview layout is not RemoteViews-compatible", it) }
    }

    @Test
    fun weeklyTracker_declaresCompletePickerEntry() = assertPickerEntry(providerFor(WeeklyTrackerWidgetReceiver::class.java))

    @Test
    fun carousel_declaresCompletePickerEntry() = assertPickerEntry(providerFor(AffirmationCarouselReceiver::class.java))
}
