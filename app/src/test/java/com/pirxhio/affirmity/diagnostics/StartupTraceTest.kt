package com.pirxhio.affirmity.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupTraceTest {

    private class RecordingSink : TraceSink {
        val events = mutableListOf<String>()
        override fun beginSection(name: String) { events += "B:$name" }
        override fun endSection() { events += "E" }
        override fun beginAsync(name: String, cookie: Int) { events += "AB:$name" }
        override fun endAsync(name: String, cookie: Int) { events += "AE:$name" }
    }

    @Test
    fun `a first-emission span ends exactly once however many emissions follow`() {
        val sink = RecordingSink()
        val span = FirstEmissionSpan("aff:x", sink)
        span.end()
        span.end()
        assertEquals(listOf("AB:aff:x", "AE:aff:x"), sink.events)
    }

    @Test
    fun `a section is closed when the block throws`() {
        val sink = RecordingSink()
        runCatching { StartupTrace.section("aff:y", sink) { error("boom") } }
        assertEquals(listOf("B:aff:y", "E"), sink.events)
    }

    @Test
    fun `an async scope is closed on exception`() {
        val sink = RecordingSink()
        runCatching { kotlinx.coroutines.runBlocking { StartupTrace.asyncScope("aff:z", sink) { error("boom") } } }
        assertEquals(listOf("AB:aff:z", "AE:aff:z"), sink.events)
    }

    @Test
    fun `spans still open are ended when the owning scope is cancelled`() {
        val sink = RecordingSink()
        val done = FirstEmissionSpan("aff:done", sink)
        val open = FirstEmissionSpan("aff:open", sink)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job())
        scope.endSpansOnCompletion(done, open)
        done.end()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        assertEquals(listOf("AB:aff:done", "AB:aff:open", "AE:aff:done", "AE:aff:open"), sink.events)
    }

    @Test
    fun `spans are ended when the owning scope fails`() {
        val sink = RecordingSink()
        val open = FirstEmissionSpan("aff:open", sink)
        val job = kotlinx.coroutines.CompletableDeferred<Unit>()
        kotlinx.coroutines.CoroutineScope(job).endSpansOnCompletion(open)
        job.completeExceptionally(IllegalStateException("boom"))
        assertEquals(listOf("AB:aff:open", "AE:aff:open"), sink.events)
    }
}
