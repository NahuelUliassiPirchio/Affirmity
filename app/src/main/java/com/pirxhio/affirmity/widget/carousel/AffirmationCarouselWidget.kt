package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.catalog.CatalogLocale
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import kotlin.random.Random
import kotlinx.coroutines.CancellationException

private const val TAG = "AffirmationCarousel"

/**
 * Home-screen carousel showing one affirmation at a time (spec: affirmation-carousel-widget).
 * Mirrors [com.pirxhio.affirmity.widget.WeeklyTrackerWidget]: everything is loaded off the main
 * thread before `provideContent`, all rules live in pure units, and the content is a pure
 * composable. Rotation is driven by [CarouselRotationWorker], which flags `advancePending` and
 * requests an update; this class consumes the flag before loading anything.
 */
class AffirmationCarouselWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Small, medium and large layouts of design 8; Glance picks the largest that fits the host cell.
    override val sizeMode: SizeMode = SizeMode.Responsive(CAROUSEL_RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = GlanceCarouselStateStore(context, id)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        // Whatever fails (state store, Room, Firebase, loading), the widget shows the replayable
        // last free frame or the placeholder instead of going blank.
        val outcome = try {
            withAdvanceConsumed(store) { advance ->
                val state = store.read()
                renderInstance(context, appWidgetId, state, advance).also { rendered ->
                    // A render for an account that signed out or switched meanwhile is never stored.
                    if (!rendered.stale) persist(store, state, rendered)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Carousel state access failed for widget $appWidgetId", e)
            RenderOutcome(CarouselState(), null)
        }

        // Strings follow the same locale the catalog text was seeded in, whichever process path
        // triggered the update (worker, receiver, or the app).
        val localized = try {
            context.withCatalogLocale()
        } catch (e: Exception) {
            Log.w(TAG, "Catalog locale lookup failed", e)
            context
        }
        val theme = themeFor(context, outcome)
        provideContent {
            CompositionLocalProvider(LocalContext provides localized) { CarouselContent(outcome.frame, theme) }
        }
    }

    /** A failed write must not hide a frame that was rendered fine. */
    private suspend fun persist(store: GlanceCarouselStateStore, basedOn: CarouselState, rendered: RenderOutcome) {
        try {
            store.update { current -> mergeRendered(current, basedOn, rendered.state) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Carousel state write failed", e)
        }
    }

    /** Header name and marker colour; never fails the render (falls back to the untitled default). */
    private suspend fun themeFor(context: Context, outcome: RenderOutcome): CarouselTheme {
        val source = outcome.state.source
        return try {
            val userCollections = if (source is CarouselSource.Collection) {
                AffirmityDatabase.getInstance(context).userCollectionDao().getAll().map { it.collection }
            } else {
                emptyList()
            }
            carouselTheme(source, outcome.state.fallback, userCollections = userCollections)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Carousel theme lookup failed", e)
            CarouselTheme.Default
        }
    }

    private suspend fun renderInstance(
        context: Context,
        appWidgetId: Int,
        state: CarouselState,
        advance: Boolean,
    ): RenderOutcome {
        var uid: String? = null
        return renderGuarded(
            state = state,
            resolveAccount = { CarouselAccount.of(FirebaseAuth.getInstance().currentUser?.uid.also { uid = it }) },
            onError = { Log.w(TAG, "Carousel render failed for widget $appWidgetId", it) },
        ) { account ->
            val database = AffirmityDatabase.getInstance(context)
            val firestore = if (uid != null) FirebaseFirestore.getInstance() else null
            val ports = AppCarouselPorts(database, firestore, uid)
            val reader = carouselAccessReader(database, firestore, uid)
            val loader = CarouselDataLoader(ports, readAccess = { reader.read() })
            val seed = appWidgetId.toLong() * 31 + System.currentTimeMillis()
            val renderer = CarouselRenderer(ShuffleQueue(Random(seed)), CarouselFrameResolver { ports.catalogRow(it) })
            renderer.render(state, advance, loader.load(state.source), System.currentTimeMillis(), account)
        }
    }
}

/** A context whose resources use the effective app/catalog locale (not just the system's). */
internal fun Context.withCatalogLocale(): Context {
    val locale = java.util.Locale.forLanguageTag(CatalogLocale.resolve().tag)
    val configuration = android.content.res.Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(configuration)
}
