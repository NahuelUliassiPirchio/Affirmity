package com.pirxhio.affirmity.ui.feed

import com.pirxhio.affirmity.personalization.scoring.PersonalizationProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecommendedSurfacesResolverTest {

    @Test
    fun `disabled ranking returns defaults without loading a profile`() = runTest {
        var loaderCalled = false

        val surfaces = resolveRecommendedSurfaces(
            rankedSurfacesEnabled = false,
            profileLoader = {
                loaderCalled = true
                throw AssertionError("disabled ranking must not load a profile")
            },
        )

        assertEquals(defaultRecommendedSurfaces(), surfaces)
        assertFalse(loaderCalled)
    }

    @Test
    fun `profile loading failure falls back to defaults`() = runTest {
        val surfaces = resolveRecommendedSurfaces(
            rankedSurfacesEnabled = true,
            profileLoader = { throw AssertionError("broken profile loader") },
        )

        assertEquals(defaultRecommendedSurfaces(), surfaces)
    }

    @Test
    fun `ranking failure falls back to defaults`() = runTest {
        val profile = emptyProfile()

        val surfaces = resolveRecommendedSurfaces(
            rankedSurfacesEnabled = true,
            profileLoader = { profile },
            ranker = { throw IllegalStateException("broken ranker") },
        )

        assertEquals(defaultRecommendedSurfaces(), surfaces)
    }

    private fun emptyProfile() = PersonalizationProfile(
        themeScores = emptyMap(),
        universeScores = emptyMap(),
        dominantTone = null,
        significantSignalCount = 0,
        isColdStart = true,
    )
}
