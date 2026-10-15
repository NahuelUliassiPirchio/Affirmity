package com.pirxhio.affirmity.widget.carousel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarouselFrameCoordinatorTest {

    /** In-memory store; [afterUpdate] runs right after an update is applied (another writer landing). */
    private class FakeStore(initial: CarouselState) : CarouselStateStore {
        var state = initial
        var updates = 0
        var beforeUpdate: suspend (call: Int) -> Unit = {}
        var afterUpdate: (call: Int) -> Unit = {}

        override suspend fun update(transform: (CarouselState) -> CarouselState): CarouselState {
            val call = ++updates
            beforeUpdate(call)
            val previous = state
            state = transform(previous)
            afterUpdate(call)
            return previous
        }
    }

    private val accountA = CarouselAccount.of("A")
    private val accountB = CarouselAccount.of("B")
    private val base = CarouselState(source = CarouselSource.Favorites, order = listOf("x", "y", "z"))
    private val themed = CarouselTheme.Default.copy(title = SourceTitle.Text("Favorites"))

    private var account = accountA

    /** A render that shows "<account> n<nonce>" and records the queue position it moved to. */
    private val renders = mutableListOf<Pair<Long, Boolean>>()
    private fun recordingRender(): suspend (CarouselState, Boolean, String) -> RenderOutcome = { state, advance, acc ->
        renders += state.refreshNonce to advance
        val next = state.copy(cursor = if (advance) state.cursor + 1 else state.cursor)
        val frame = CarouselFrame("$acc n${state.refreshNonce}", null, null, isFreeTier = true)
        RenderOutcome(next.withShown(frame, acc), frame)
    }

    private fun coordinator(
        store: FakeStore,
        render: suspend (CarouselState, Boolean, String) -> RenderOutcome = recordingRender(),
        style: suspend (CarouselState) -> CarouselStyle = { CarouselStyle(themed) },
    ) = CarouselFrameCoordinator(
        store = store,
        resolveAccount = { account },
        render = render,
        style = style,
    )

    private fun TestScope.start(coordinator: CarouselFrameCoordinator) =
        backgroundScope.launch { coordinator.run() }.also { runCurrent() }

    @Test
    fun `two sizes share one load per refresh and see the same frame`() = runTest {
        val store = FakeStore(base)
        val coordinator = coordinator(store)
        start(coordinator)
        val small = mutableListOf<CarouselView?>()
        val large = mutableListOf<CarouselView?>()
        backgroundScope.launch { coordinator.views.collect { small += it } }
        backgroundScope.launch { coordinator.views.collect { large += it } }
        runCurrent()

        requestRefresh(store) {}
        coordinator.request(1)
        coordinator.request(1)
        runCurrent()

        assertEquals(listOf(0L to false, 1L to false), renders)
        assertEquals("$accountA n1", small.last()?.frame?.text)
        assertEquals(small.last(), large.last())
    }

    @Test
    fun `a request already covered by the shown frame does not load again`() = runTest {
        val store = FakeStore(base.copy(refreshNonce = 4))
        val coordinator = coordinator(store)
        start(coordinator)

        coordinator.request(4)
        coordinator.request(3)
        runCurrent()

        assertEquals(1, renders.size)
    }

    @Test
    fun `the first view is available to await before content is provided`() = runTest {
        val store = FakeStore(base)
        val coordinator = coordinator(store)
        start(coordinator)

        val first = coordinator.awaitFirstView()

        assertEquals("$accountA n0", first.frame?.text)
        assertEquals(themed, first.theme)
    }

    @Test
    fun `an advance landing right after a load captured its state is applied by the next load`() = runTest {
        val store = FakeStore(base)
        // The worker raises the advance between the load's capture and anything else it does.
        store.afterUpdate = { call -> if (call == 1) store.state = store.state.copy(advancePending = true).withRefreshRequested() }
        val coordinator = coordinator(store)

        start(coordinator)

        assertEquals(listOf(0L to false, 1L to true), renders)
        assertFalse(store.state.advancePending)
        assertEquals(1, store.state.cursor)
        assertEquals("$accountA n1", coordinator.views.value?.frame?.text)
    }

    @Test
    fun `an advance raised while rendering is consumed exactly once`() = runTest {
        val store = FakeStore(base)
        val gate = CompletableDeferred<Unit>()
        val record = recordingRender()
        val coordinator = coordinator(store, render = { state, advance, acc ->
            if (renders.isEmpty()) gate.await()
            record(state, advance, acc)
        })
        start(coordinator)

        requestAdvance(store) {}
        coordinator.request(1)
        gate.complete(Unit)
        runCurrent()
        coordinator.request(1)
        runCurrent()

        assertEquals(listOf(0L to false, 1L to true), renders)
        assertFalse(store.state.advancePending)
        assertEquals(1, store.state.cursor)
    }

    @Test
    fun `an account switch while persisting publishes the placeholder`() = runTest {
        val store = FakeStore(base)
        store.beforeUpdate = { call -> if (call == 2) account = accountB }
        val coordinator = coordinator(store)

        start(coordinator)

        val view = coordinator.views.value
        assertNotNull(view)
        assertNull(view?.frame)
        assertEquals(CarouselTheme.Default, view?.theme)
    }

    @Test
    fun `an account switch during the theme lookup publishes the placeholder`() = runTest {
        val store = FakeStore(base)
        val coordinator = coordinator(store, style = {
            account = accountB
            CarouselStyle(themed)
        })

        start(coordinator)

        assertNull(coordinator.views.value?.frame)
        assertNotNull(coordinator.views.value)
    }

    @Test
    fun `an account switch while rendering publishes the placeholder and stores nothing`() = runTest {
        val store = FakeStore(base)
        val record = recordingRender()
        val coordinator = coordinator(store, render = { state, advance, acc ->
            record(state, advance, acc).also { account = accountB }
        })

        start(coordinator)

        assertNull(coordinator.views.value?.frame)
        assertEquals(1, store.updates)
    }

    @Test
    fun `a load that times out replays the account's last free frame`() = runTest {
        val stored = base.withShown(CarouselFrame("Kept", "sub", null, isFreeTier = true), accountA)
        val store = FakeStore(stored)
        val coordinator = coordinator(store, render = { _, _, _ -> awaitCancellation() })
        start(coordinator)

        advanceTimeBy(CAROUSEL_LOAD_TIMEOUT_MILLIS + 1)

        assertEquals(CarouselFrame("Kept", "sub", null, isFreeTier = true), coordinator.views.value?.frame)
        assertEquals(1, store.updates)
    }

    @Test
    fun `a load that times out without a replay shows the placeholder, never blank`() = runTest {
        val stored = base.withShown(CarouselFrame("Not yours", null, null, isFreeTier = true), accountB)
        val store = FakeStore(stored)
        val coordinator = coordinator(store, render = { _, _, _ -> awaitCancellation() })
        start(coordinator)

        advanceTimeBy(CAROUSEL_LOAD_TIMEOUT_MILLIS + 1)

        val view = coordinator.views.value
        assertNotNull(view)
        assertNull(view?.frame)
    }

    @Test
    fun `a state store that fails shows the placeholder`() = runTest {
        val store = FakeStore(base)
        store.beforeUpdate = { throw java.io.IOException("disk") }
        val coordinator = coordinator(store)

        start(coordinator)

        assertNotNull(coordinator.views.value)
        assertNull(coordinator.views.value?.frame)
    }

    @Test
    fun `cancelling the session stops the loader without storing a partial render`() = runTest {
        val store = FakeStore(base.copy(advancePending = true))
        val rendering = CompletableDeferred<Unit>()
        val coordinator = coordinator(store, render = { _, _, _ ->
            rendering.complete(Unit)
            awaitCancellation()
        })
        val session = start(coordinator)
        rendering.await()

        session.cancel()
        runCurrent()

        assertTrue(session.isCancelled)
        assertEquals(1, store.updates)
        assertEquals(base, store.state)
    }

    @Test
    fun `a published frame carries the account it was rendered for`() = runTest {
        val coordinator = coordinator(FakeStore(base))
        start(coordinator)

        assertEquals(accountA, coordinator.views.value?.account)
    }

    @Test
    fun `a placeholder carries no account`() = runTest {
        val store = FakeStore(base)
        store.beforeUpdate = { throw java.io.IOException("disk") }
        val coordinator = coordinator(store)
        start(coordinator)

        assertNull(coordinator.views.value?.account)
    }

    @Test
    fun `a reload request loads again even when the nonce is covered`() = runTest {
        val store = FakeStore(base)
        val coordinator = coordinator(store)
        start(coordinator)
        assertEquals("$accountA n0", coordinator.views.value?.frame?.text)

        account = accountB
        coordinator.requestReload()
        runCurrent()

        assertEquals(2, renders.size)
        assertEquals(accountB, coordinator.views.value?.account)
    }

    @Test
    fun `cancellation while checking the account before publishing is not swallowed`() = runTest {
        val store = FakeStore(base)
        var calls = 0
        val coordinator = CarouselFrameCoordinator(
            store = store,
            resolveAccount = { if (++calls >= 3) throw kotlinx.coroutines.CancellationException("done") else account },
            render = recordingRender(),
            style = { CarouselStyle(themed) },
        )

        val session = start(coordinator)

        assertTrue(session.isCancelled)
        assertNull(coordinator.views.value)
    }
}
