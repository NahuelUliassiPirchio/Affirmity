package com.pirxhio.affirmity.ui.feed

import com.pirxhio.affirmity.personalization.scoring.PersonalizationProfile
import com.pirxhio.affirmity.personalization.scoring.PersonalizationScoring
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.SignalType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceUiModelTest {

    @Test
    fun `ranking orders surfaces by descending universe score with catalog-order ties`() {
        val defaults = defaultRecommendedSurfaces()
        val thirdSurface = defaults[2]
        val lastSurface = defaults.last()
        val profile = profile(
            universeScores = mapOf(
                thirdSurface.id to 4.0,
                lastSurface.id to 9.0,
            ),
        )

        val ranked = rankedRecommendedSurfaces(profile)

        assertEquals(listOf(lastSurface.id, thirdSurface.id), ranked.take(2).map { it.id })
        assertEquals(
            defaults.map { it.id }.filterNot { it == lastSurface.id || it == thirdSurface.id },
            ranked.drop(2).map { it.id },
        )
    }

    @Test
    fun `ranking picks the three highest-scored themes with catalog-order ties`() {
        val surface = defaultRecommendedSurfaces().first { it.themeIds.size >= 4 }
        val profile = profile(
            themeScores = mapOf(
                surface.themeIds[2] to 7.0,
                surface.themeIds[3] to 11.0,
            ),
        )

        val rankedSurface = rankedRecommendedSurfaces(profile).first { it.id == surface.id }

        assertEquals(
            listOf(surface.themeIds[3], surface.themeIds[2], surface.themeIds[0]),
            rankedSurface.recommendedThemeIds,
        )
        assertEquals(surface.themeIds, rankedSurface.themeIds)
    }

    @Test
    fun `enabled ranking uses a real scored profile for surface and theme order`() = runTest {
        val defaults = defaultRecommendedSurfaces()
        val targetSurface = defaults.last { it.themeIds.size >= 3 }
        val targetTheme = targetSurface.themeIds.last()
        val now = 1_700_000_000_000L
        val profile = PersonalizationScoring.profile(
            signals = listOf(
                PersonalizationSignal(
                    type = SignalType.AFFIRMATION_SAVED,
                    themeId = targetTheme,
                    groupId = targetSurface.id,
                    tone = null,
                    occurredAtMillis = now,
                ),
            ),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )

        val ranked = resolveRecommendedSurfaces(
            rankedSurfacesEnabled = true,
            profileLoader = { profile },
        )

        assertEquals(targetSurface.id, ranked.first().id)
        assertEquals(
            listOf(targetTheme, targetSurface.themeIds[0], targetSurface.themeIds[1]),
            ranked.first().recommendedThemeIds,
        )
    }

    @Test
    fun `empty scored profile is identical to default surfaces`() {
        val profile = PersonalizationScoring.profile(
            signals = emptyList(),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = 1_700_000_000_000L,
        )

        assertEquals(defaultRecommendedSurfaces(), rankedRecommendedSurfaces(profile))
    }

    private fun profile(
        themeScores: Map<String, Double> = emptyMap(),
        universeScores: Map<String, Double> = emptyMap(),
    ) = PersonalizationProfile(
        themeScores = themeScores,
        universeScores = universeScores,
        dominantTone = null,
        significantSignalCount = 0,
        isColdStart = true,
    )
}
