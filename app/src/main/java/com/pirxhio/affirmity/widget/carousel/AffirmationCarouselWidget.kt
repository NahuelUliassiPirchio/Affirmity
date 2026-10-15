package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val TAG = "AffirmationCarousel"

/**
 * Home-screen carousel showing one affirmation at a time (spec: affirmation-carousel-widget).
 * All rules live in pure units and the content is a pure composable. Each session runs one
 * [CarouselFrameCoordinator] that loads frames one at a time and publishes them to every
 * responsive size; while the session keeps running, `update` does not call `provideGlance` again,
 * so the coordinator reloads whenever a caller raised the stored refresh nonce (rotation, app
 * refreshes, configure). Rotation is driven by [CarouselRotationWorker], which flags
 * `advancePending` and requests an update; each load consumes the flag with its state capture.
 */
class AffirmationCarouselWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Small, medium and large layouts of design 8; Glance picks the largest that fits the host cell.
    override val sizeMode: SizeMode = SizeMode.Responsive(CAROUSEL_RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val coordinator = CarouselFrameCoordinator(
            store = GlanceCarouselStateStore(context, id),
            resolveAccount = ::currentCarouselAccount,
            render = { state, advance, account -> renderInstance(context, appWidgetId, state, advance, account) },
            style = { state -> CarouselStyle(themeFor(context, state), localized(context)) },
            onError = { Log.w(TAG, "Carousel load failed for widget $appWidgetId", it) },
        )
        // The loader lives in the session scope and is cancelled with it. The first frame is
        // awaited (bounded by the load timeout) so the widget never starts blank.
        coroutineScope {
            launch { coordinator.run() }
            coordinator.awaitFirstView()
            provideContent {
                CarouselSessionContent(
                    views = coordinator.views,
                    onRefreshRequested = coordinator::request,
                    currentAccount = ::currentCarouselAccountOrNull,
                    onReloadRequested = coordinator::requestReload,
                )
            }
        }
    }

    /**
     * Strings follow the same locale the catalog text was seeded in, whichever process path
     * triggered the update (worker, receiver, or the app).
     */
    private fun localized(context: Context): Context = try {
        context.withCatalogLocale()
    } catch (e: Exception) {
        Log.w(TAG, "Catalog locale lookup failed", e)
        context
    }

    /** Header name and marker colour; never fails the render (falls back to the untitled default). */
    private suspend fun themeFor(context: Context, state: CarouselState): CarouselTheme {
        val source = state.source
        return try {
            val userCollections = if (source is CarouselSource.Collection) {
                AffirmityDatabase.getInstance(context).userCollectionDao().getAll().map { it.collection }
            } else {
                emptyList()
            }
            carouselTheme(source, state.fallback, userCollections = userCollections)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Carousel theme lookup failed", e)
            CarouselTheme.Default
        }
    }

    /** Loads and renders one frame for [account]; failures are handled by the coordinator's guard. */
    private suspend fun renderInstance(
        context: Context,
        appWidgetId: Int,
        state: CarouselState,
        advance: Boolean,
        account: String,
    ): RenderOutcome {
        val uid = CarouselAccount.uidOf(account)
        val database = AffirmityDatabase.getInstance(context)
        val firestore = if (uid != null) FirebaseFirestore.getInstance() else null
        val ports = AppCarouselPorts(database, firestore, uid)
        val reader = carouselAccessReader(database, firestore, uid)
        val loader = CarouselDataLoader(ports, readAccess = { reader.read() })
        val seed = appWidgetId.toLong() * 31 + System.currentTimeMillis()
        val renderer = CarouselRenderer(ShuffleQueue(Random(seed)), CarouselFrameResolver { ports.catalogRow(it) })
        return renderer.render(state, advance, loader.load(state.source), System.currentTimeMillis(), account)
    }
}

/** A context whose resources use the effective app/catalog locale (not just the system's). */
internal fun Context.withCatalogLocale(): Context {
    val locale = java.util.Locale.forLanguageTag(CatalogLocale.resolve().tag)
    val configuration = android.content.res.Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(configuration)
}

/** The signed-in account right now; `FirebaseAuth.currentUser` is a synchronous getter. */
private fun currentCarouselAccount(): String = CarouselAccount.of(FirebaseAuth.getInstance().currentUser?.uid)

/** Same, for rendering: an unresolvable account is `null` and draws the placeholder. */
private fun currentCarouselAccountOrNull(): String? = try {
    currentCarouselAccount()
} catch (e: Exception) {
    null
}
