package com.pirxhio.affirmity.data.catalog

import androidx.core.os.LocaleListCompat
import com.pirxhio.affirmity.data.local.CatalogAffirmationDao
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

private const val COLLECTION = "self_worth.feeling_enough.intrinsic_worth"

private fun json(version: String) =
    """{"version":"$version","affirmations":[{"id":"cat_$COLLECTION.001","title":"T","subtitle":"S",""" +
        """"groupId":"self_worth","themeId":"self_worth.feeling_enough","collectionId":"$COLLECTION",""" +
        """"sortOrder":0,"tone":"powerful","semanticAngle":"identity"}]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogLocaleSwitcherTest {

    private val events = mutableListOf<String>()
    private var effective = CatalogLocale.ES
    private var replaceDelayMs = 0L
    private var failNextReplace = false
    private val replaceByLocale = mutableListOf<CatalogLocale>()
    private val marker = MutableStateFlow<String?>(null)
    private var lastReadLocale = CatalogLocale.ES

    private val dao = object : CatalogAffirmationDao {
        override fun observeAll(): Flow<List<CatalogAffirmationEntity>> = throw NotImplementedError()
        override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> =
            throw NotImplementedError()
        override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> = emptyList()
        override suspend fun count(): Int = 1
        override suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) {
            if (failNextReplace) {
                failNextReplace = false
                throw IllegalStateException("disk full")
            }
            events += "replace:${lastReadLocale.tag}"
            replaceByLocale += lastReadLocale
            if (replaceDelayMs > 0) delay(replaceDelayMs)
        }
        override suspend fun insertAll(rows: List<CatalogAffirmationEntity>) = throw NotImplementedError()
        override suspend fun deleteAll() = throw NotImplementedError()
    }

    private val prefs = object : CatalogPreferences {
        override fun observeSeededCatalogVersion(): Flow<String?> = marker
        override suspend fun saveSeededCatalogVersion(version: String) {
            events += "marker:$version"
            marker.value = version
        }
    }

    private val seeder = CatalogSeeder(
        assetReader = { locale ->
            lastReadLocale = locale
            readOnSeedDispatcher += seedDispatchDepth > 0
            json("1.0.0")
        },
        dao = dao,
        prefs = prefs,
        currentLocale = { effective },
        knownCollectionIds = { setOf(COLLECTION) },
    )

    private var seedDispatchDepth = 0
    private val readOnSeedDispatcher = mutableListOf<Boolean>()

    private val locales = LocaleListCompat.getEmptyLocaleList()

    private fun TestScope.switcher(
        applyEffect: () -> Unit = {},
        scope: CoroutineScope = this,
        seedDispatcher: CoroutineDispatcher? = null,
    ) = CatalogLocaleSwitcher(
        scope = scope,
        seeder = seeder,
        applyLocales = {
            events += "apply"
            applyEffect()
        },
        seedDispatcher = seedDispatcher ?: StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.trackingSeedDispatcher(): CoroutineDispatcher {
        val inner = StandardTestDispatcher(testScheduler)
        return object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                inner.dispatch(context, Runnable {
                    seedDispatchDepth++
                    try {
                        block.run()
                    } finally {
                        seedDispatchDepth--
                    }
                })
            }
        }
    }

    @Test
    fun `happy path runs seed then apply then reconcile`() = runTest {
        val sw = switcher(applyEffect = { effective = CatalogLocale.EN })

        sw.switch(CatalogLocale.EN, locales).join()

        assertEquals(listOf("replace:en", "marker:1.0.0|en", "apply"), events)
        assertEquals("1.0.0|en", marker.value) // reconcile resolved EN -> marker matched -> no-op
    }

    @Test
    fun `a seed slower than the timeout does not delay apply and is not cancelled`() = runTest {
        replaceDelayMs = 4_000
        val sw = switcher(applyEffect = { effective = CatalogLocale.EN })

        val job = sw.switch(CatalogLocale.EN, locales)
        advanceTimeBy(1_499)
        runCurrent()
        assertTrue("apply must wait for the timeout", "apply" !in events)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1_500L, currentTime)
        assertTrue("apply fires at 1500 ms", "apply" in events)
        assertTrue("seed still running", "marker:1.0.0|en" !in events)

        advanceUntilIdle()
        job.join()
        assertEquals("1.0.0|en", marker.value)
        assertEquals(listOf(CatalogLocale.EN), replaceByLocale)
    }

    @Test
    fun `when apply is a no-op the reconcile re-seeds the still-effective locale`() = runTest {
        marker.value = "1.0.0|es"
        effective = CatalogLocale.ES
        val sw = switcher() // applyLocales does NOT change the resolver

        sw.switch(CatalogLocale.EN, locales).join()

        assertEquals(listOf(CatalogLocale.EN, CatalogLocale.ES), replaceByLocale)
        assertEquals("1.0.0|es", marker.value)
    }

    @Test
    fun `selecting the already-seeded locale does not replace rows again`() = runTest {
        marker.value = "1.0.0|en"
        effective = CatalogLocale.EN
        val sw = switcher()

        sw.switch(CatalogLocale.EN, locales).join()

        assertTrue(replaceByLocale.isEmpty())
        assertEquals(listOf("apply"), events)
    }

    @Test
    fun `a failing pre-seed is survived and the reconcile retries`() = runTest {
        failNextReplace = true
        val sw = switcher(applyEffect = { effective = CatalogLocale.EN })

        sw.switch(CatalogLocale.EN, locales).join()

        assertTrue("apply still happens", "apply" in events)
        assertEquals(listOf(CatalogLocale.EN), replaceByLocale) // reconcile retried and succeeded
        assertEquals("1.0.0|en", marker.value)
    }

    @Test
    fun `the process scope outlives the caller cancelling`() = runTest {
        replaceDelayMs = 500
        val processScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val sw = switcher(applyEffect = { effective = CatalogLocale.EN }, scope = processScope)
        val callerScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))

        callerScope.launch { sw.switch(CatalogLocale.EN, locales) }
        runCurrent()
        callerScope.cancel() // e.g. the Settings composition leaves / the activity is recreated
        advanceUntilIdle()

        assertTrue("apply" in events)
        assertEquals("1.0.0|en", marker.value)
        processScope.cancel()
    }

    @Test
    fun `the reconcile seed runs on the seed dispatcher, not the process scope dispatcher`() = runTest {
        marker.value = "1.0.0|es"
        effective = CatalogLocale.ES
        val sw = switcher(seedDispatcher = trackingSeedDispatcher()) // apply is a no-op -> reconcile re-seeds ES

        sw.switch(CatalogLocale.EN, locales).join()

        assertEquals(listOf(CatalogLocale.EN, CatalogLocale.ES), replaceByLocale)
        assertEquals("pre-seed and reconcile must both read off the process dispatcher",
            listOf(true, true), readOnSeedDispatcher)
    }
}
