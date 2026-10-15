package com.pirxhio.affirmity.widget.carousel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.LocalState
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A running Glance session never calls `provideGlance` again: `update` only replaces the session's
 * state (`LocalState`, a snapshot state) and recomposes. These tests drive the content exactly that
 * way, which is the closest seam the unit-test harness offers to a live session: it cannot replace
 * the state once composed, so the test provides `LocalState` itself, as `AppWidgetSession` does.
 * The harness also only re-snapshots its node tree on some recomposer idle transitions, so later
 * frames are observed through the content slot rather than `onNode`. Emulator only.
 */
class CarouselLiveContentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun prefs(nonce: Long, extra: String = ""): Preferences =
        mutablePreferencesOf(CarouselStateCodec.REFRESH_NONCE to nonce, stringPreferencesKey("other") to extra)

    private fun view(text: String, nonce: Long) =
        CarouselView(CarouselFrame(text, null, null, isFreeTier = true), CarouselTheme.Default, nonce)

    @Test
    fun aRefreshDuringALiveSessionRendersTheNewFrame() = runTest {
        var state by mutableStateOf(prefs(0), neverEqualPolicy())
        var loads = 0
        var drawn: String? = null
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(DpSize(250.dp, 110.dp))
            provideComposable {
                CompositionLocalProvider(LocalState provides state) {
                    CarouselLiveContent(
                        initial = view("Before sign-out", 0),
                        reload = {
                            loads++
                            view("After sign-out", 1)
                        },
                    ) {
                        drawn = it.frame?.text
                        CarouselContent(it.frame, it.theme)
                    }
                }
            }
            onNode(hasText("Before sign-out")).assertExists()

            Snapshot.withMutableSnapshot { state = prefs(1) }
            awaitIdle()

            assertEquals("After sign-out", drawn)
        }
        assertEquals(1, loads)
    }

    @Test
    fun aStateChangeWithoutANewRefreshKeepsTheFrameWithoutReloading() = runTest {
        var state by mutableStateOf(prefs(2), neverEqualPolicy())
        var loads = 0
        var drawn: String? = null
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(DpSize(250.dp, 110.dp))
            provideComposable {
                CompositionLocalProvider(LocalState provides state) {
                    CarouselLiveContent(
                        initial = view("Shown", 2),
                        reload = {
                            loads++
                            view("Reloaded", 2)
                        },
                    ) {
                        drawn = it.frame?.text
                        CarouselContent(it.frame, it.theme)
                    }
                }
            }

            // The render's own write (queue, last frame) changes the state but not the nonce.
            Snapshot.withMutableSnapshot { state = prefs(2, extra = "persisted by the render") }
            awaitIdle()

            assertEquals("Shown", drawn)
        }
        assertEquals(0, loads)
    }
}
