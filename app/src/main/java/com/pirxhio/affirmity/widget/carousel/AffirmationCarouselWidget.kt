package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import kotlin.random.Random
import kotlinx.coroutines.CancellationException

private const val TAG = "AffirmationCarousel"

/**
 * Home-screen carousel showing one affirmation at a time (spec: affirmation-carousel-widget).
 * Mirrors [com.pirxhio.affirmity.widget.WeeklyTrackerWidget]: everything is loaded off the main
 * thread before `provideContent`, all rules live in pure units, and the content is a pure
 * composable. Rotation is driven by [CarouselRotationWorker], which flags `advancePending` and
 * requests an update; this class consumes the flag before loading anything (D4/W3).
 */
class AffirmationCarouselWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = GlanceCarouselStateStore(context, id)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        val outcome = withAdvanceConsumed(store) { advance ->
            val state = store.read()
            renderInstance(context, appWidgetId, state, advance).also { rendered ->
                // Keep any advance request a worker raised while this render was loading.
                store.update { current -> rendered.state.copy(advancePending = current.advancePending) }
            }
        }

        provideContent { CarouselContent(outcome.frame) }
    }

    private suspend fun renderInstance(
        context: Context,
        appWidgetId: Int,
        state: CarouselState,
        advance: Boolean,
    ): RenderOutcome {
        val database = AffirmityDatabase.getInstance(context)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val firestore = if (uid != null) FirebaseFirestore.getInstance() else null
        val ports = AppCarouselPorts(database, firestore, uid)
        val reader = carouselAccessReader(database, firestore, uid)
        val loader = CarouselDataLoader(ports, readAccess = { reader.read() })
        val seed = appWidgetId.toLong() * 31 + System.currentTimeMillis()
        val renderer = CarouselRenderer(ShuffleQueue(Random(seed)), CarouselFrameResolver { ports.catalogRow(it) })
        return try {
            renderer.render(state, advance, loader.load(state.source), System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never blank and never crash the host: keep the last free text if there is one.
            Log.w(TAG, "Carousel render failed for widget $appWidgetId", e)
            val last = state.replayableLast()
            RenderOutcome(state, last?.let { CarouselFrame(it.text, it.subtitle, null, isFreeTier = true) })
        }
    }
}
