package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.local.FavoriteAffirmationEntity
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pipeline tests over a real in-memory Room database: loader -> pool -> queue -> frame, as
 * `provideGlance` runs it. Emulator only.
 */
@RunWith(AndroidJUnit4::class)
class CarouselRefreshInstrumentedTest {

    private lateinit var database: AffirmityDatabase
    private lateinit var ports: AppCarouselPorts

    private val universe = "body_energy_wellbeing"
    private val freeCollection = "body_energy_wellbeing.body_acceptance.respect_my_body_today"
    private val proCollection = "body_energy_wellbeing.body_confidence.take_physical_space"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ports = AppCarouselPorts(database, firestore = null, uid = null)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun row(id: String, text: String, collectionId: String = freeCollection) = CatalogAffirmationEntity(
        id = id, text = text, subtitle = "", groupId = universe, themeId = "$universe.theme",
        collectionId = collectionId, sortOrder = 0,
    )

    private suspend fun render(
        state: CarouselState,
        access: CarouselAccess = CarouselAccess.FailClosed,
    ): RenderOutcome {
        val loader = CarouselDataLoader(ports, readAccess = { access })
        val renderer = CarouselRenderer(ShuffleQueue(Random(1)), CarouselFrameResolver { ports.catalogRow(it) })
        return renderer.render(state, advance = false, load = loader.load(state.source), nowMillis = 0, account = CarouselAccount.SIGNED_OUT)
    }

    @Test
    fun localeChange_textFollowsThePoolWithoutReshuffling() = runBlocking {
        database.catalogAffirmationDao().replaceAll(listOf(row("cat_a", "texto en español")))
        val first = render(CarouselState(source = CarouselSource.Theme(universe)))
        assertEquals("texto en español", first.frame?.text)

        // The locale switch re-seeds the same ids with the other language.
        database.catalogAffirmationDao().replaceAll(listOf(row("cat_a", "text in english")))
        val second = render(first.state)

        assertEquals("text in english", second.frame?.text)
        assertEquals(first.state.order, second.state.order)
    }

    @Test
    fun newFavorite_joinsTheFavoritesPool() = runBlocking {
        database.catalogAffirmationDao().replaceAll(listOf(row("cat_a", "A"), row("cat_b", "B")))
        database.favoriteAffirmationDao().insert(FavoriteAffirmationEntity("cat_a", 1))
        val first = render(CarouselState(source = CarouselSource.Favorites))
        assertEquals(listOf("cat_a"), first.state.order)

        database.favoriteAffirmationDao().insert(FavoriteAffirmationEntity("cat_b", 2))
        val second = render(first.state)

        assertEquals(setOf("cat_a", "cat_b"), second.state.order.toSet())
        assertFalse(second.state.fallback)
    }

    @Test
    fun lapse_proTextBecomesFreeFallbackWithNoGatedTextStored() = runBlocking {
        database.catalogAffirmationDao().replaceAll(
            listOf(row("cat_free", "free text"), row("cat_pro", "pro text", proCollection)),
        )
        val pro = CarouselAccess(AccessTier.PRO, AdUnlockState())
        val state = CarouselState(source = CarouselSource.Theme(universe), order = listOf("cat_pro", "cat_free"))
        val whilePro = render(state, pro)
        assertEquals("pro text", whilePro.frame?.text)

        val afterLapse = render(whilePro.state, CarouselAccess.FailClosed)

        assertEquals("free text", afterLapse.frame?.text)
        assertTrue(afterLapse.frame!!.isFreeTier)
        assertFalse("cat_pro" in afterLapse.state.order)
        assertTrue(afterLapse.state.lastText != "pro text")
    }
}
