package com.pirxhio.affirmity.widget.carousel

import android.content.Intent
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.test.platform.app.InstrumentationRegistry
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.R
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Glance unit tests for the pure carousel content. Run on an emulator only (see device-test notes). */
class CarouselContentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun frame(notice: FallbackReason? = null, subtitle: String? = "Always") =
        CarouselFrame(text = "I am enough", subtitle = subtitle, notice = notice, isFreeTier = true)

    @Test
    fun showsTextAndSubtitle() = runTest {
        runGlanceAppWidgetUnitTest {
            provideComposable { CarouselContent(frame()) }

            onNode(hasText("I am enough")).assertExists()
            onNode(hasText("Always")).assertExists()
        }
    }

    @Test
    fun omitsSubtitleWhenAbsent() = runTest {
        runGlanceAppWidgetUnitTest {
            provideComposable { CarouselContent(frame(subtitle = null)) }

            onNode(hasText("I am enough")).assertExists()
            onNode(hasText("Always")).assertDoesNotExist()
        }
    }

    @Test
    fun noticeIsShownOnlyForFallback() = runTest {
        val notice = context.getString(noticeStringRes(FallbackReason.SOURCE_LOCKED))
        runGlanceAppWidgetUnitTest {
            provideComposable { CarouselContent(frame(notice = FallbackReason.SOURCE_LOCKED)) }
            onNode(hasText(notice)).assertExists()
        }
        runGlanceAppWidgetUnitTest {
            provideComposable { CarouselContent(frame(notice = null)) }
            onNode(hasText(notice)).assertDoesNotExist()
        }
    }

    @Test
    fun emptyCatalogShowsThePlaceholder() = runTest {
        runGlanceAppWidgetUnitTest {
            provideComposable { CarouselContent(null) }

            onNode(hasText(context.getString(R.string.carousel_placeholder))).assertExists()
        }
    }

    @Test
    fun tappingTheBodyStartsMainActivity() = runTest {
        runGlanceAppWidgetUnitTest {
            provideComposable { CarouselContent(frame()) }

            onNode(hasStartActivityClickAction<MainActivity>()).assertExists()
        }
    }

    @Test
    fun hasNoPreviousOrNextControls() = runTest {
        runGlanceAppWidgetUnitTest {
            provideComposable { CarouselContent(frame()) }

            // The only clickable node is the root tap target that opens the app.
            onAllNodes(hasClickAction()).assertCountEquals(1)
        }
    }
}
