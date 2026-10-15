package com.pirxhio.affirmity.widget.carousel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.LocalState
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every responsive size only draws what the session's coordinator published to the shared flow and
 * reports the refresh nonce it sees; none of them loads. A running session never calls
 * `provideGlance` again (`update` only replaces `LocalState`), so the test provides `LocalState`
 * itself, as `AppWidgetSession` does. Emulator only.
 */
class CarouselSessionContentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun prefs(nonce: Long): Preferences = mutablePreferencesOf(CarouselStateCodec.REFRESH_NONCE to nonce)

    private fun view(text: String, nonce: Long) =
        CarouselView(CarouselFrame(text, null, null, isFreeTier = true), CarouselTheme.Default, nonce)

    private fun drawsTheSharedFrameAt(size: DpSize) = runTest {
        val views = MutableStateFlow<CarouselView?>(view("Shared frame", 0))
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(size)
            provideComposable {
                CompositionLocalProvider(LocalState provides prefs(0)) {
                    CarouselSessionContent(views, onRefreshRequested = {})
                }
            }

            onNode(hasText("Shared frame")).assertExists()
        }
    }

    @Test
    fun theSmallSizeDrawsTheSharedFrame() = drawsTheSharedFrameAt(CAROUSEL_RESPONSIVE_SIZES.elementAt(0))

    @Test
    fun theMediumSizeDrawsTheSharedFrame() = drawsTheSharedFrameAt(CAROUSEL_RESPONSIVE_SIZES.elementAt(1))

    @Test
    fun theLargeSizeDrawsTheSharedFrame() = drawsTheSharedFrameAt(CAROUSEL_RESPONSIVE_SIZES.elementAt(2))

    @Test
    fun aRefreshDuringALiveSessionIsReportedToTheCoordinator() = runTest {
        var state by mutableStateOf(prefs(2), neverEqualPolicy())
        val reported = mutableListOf<Long>()
        val views = MutableStateFlow<CarouselView?>(view("Shown", 2))
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(CAROUSEL_RESPONSIVE_SIZES.elementAt(1))
            provideComposable {
                CompositionLocalProvider(LocalState provides state) {
                    CarouselSessionContent(views, onRefreshRequested = { reported += it })
                }
            }
            onNode(hasText("Shown")).assertExists()

            Snapshot.withMutableSnapshot { state = prefs(3) }
            awaitIdle()
        }
        assertEquals(listOf(2L, 3L), reported)
    }
}
