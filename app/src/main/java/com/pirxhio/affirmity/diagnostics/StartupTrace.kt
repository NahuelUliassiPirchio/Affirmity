package com.pirxhio.affirmity.diagnostics

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Where trace events go. Indirection exists so the open/close discipline is JVM-testable. */
internal interface TraceSink {
    fun beginSection(name: String)
    fun endSection()
    fun beginAsync(name: String, cookie: Int)
    fun endAsync(name: String, cookie: Int)
}

/** `android.os.Trace`-backed sink. When no trace session is active every call is a cheap
 * tag-enabled check in the platform, so instrumentation is free in normal runs. Async sections
 * need API 29; below that they are skipped (sync sections exist since API 18). */
internal object PlatformTraceSink : TraceSink {
    override fun beginSection(name: String) = Trace.beginSection(name)
    override fun endSection() = Trace.endSection()
    override fun beginAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(name, cookie)
    }
    override fun endAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(name, cookie)
    }
}

/**
 * Startup tracing helpers, all spans prefixed `aff:`. Use [section] for code that begins and ends
 * on one thread without suspending; use [asyncScope] / [FirstEmissionSpan] for anything that
 * suspends or spans threads (a sync section opened before a suspension may close on another thread
 * and corrupt the trace).
 */
internal object StartupTrace {
    private val cookies = AtomicInteger()

    fun nextCookie(): Int = cookies.incrementAndGet()

    inline fun <T> section(name: String, sink: TraceSink = PlatformTraceSink, block: () -> T): T {
        sink.beginSection(name)
        try {
            return block()
        } finally {
            sink.endSection()
        }
    }

    suspend inline fun <T> asyncScope(name: String, sink: TraceSink = PlatformTraceSink, block: () -> T): T {
        val cookie = nextCookie()
        sink.beginAsync(name, cookie)
        try {
            return block()
        } finally {
            sink.endAsync(name, cookie)
        }
    }
}

/** Async span opened on construction and closed by the first [end]; later calls are no-ops, so it
 * can be ended from every emission of a flow collector. */
internal class FirstEmissionSpan(
    private val name: String,
    private val sink: TraceSink = PlatformTraceSink,
) {
    private val cookie = StartupTrace.nextCookie()
    private val ended = AtomicBoolean(false)

    init {
        sink.beginAsync(name, cookie)
    }

    fun end() {
        if (ended.compareAndSet(false, true)) sink.endAsync(name, cookie)
    }
}

/** Ends [spans] when this scope's Job completes, is cancelled or fails, so none outlive a first emission that never comes. */
internal fun kotlinx.coroutines.CoroutineScope.endSpansOnCompletion(vararg spans: FirstEmissionSpan) {
    coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { spans.forEach { it.end() } }
}
