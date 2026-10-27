package com.pirxhio.affirmity.ui.progress

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.HealerActivation
import com.pirxhio.affirmity.data.StreakHealerState
import com.pirxhio.affirmity.data.WeeklyStreak
import com.pirxhio.affirmity.ui.components.FloatingStatusReservedEnd
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProgressScreenTitleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun titleStopsBeforeTheFloatingProfileButton() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2.5f)) {
                MaterialTheme {
                    Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(320.dp)) {
                        ProgressScreen(
                            affirmationsStreak = WeeklyStreak(List(7) { false }, 0),
                            meditationStreak = WeeklyStreak(List(7) { false }, 0),
                            streakHealer = StreakHealerState(0, false, 0, 0, emptySet(), HealerActivation.Unavailable),
                            onActivateHealer = {},
                        )
                    }
                }
            }
        }
        val title = compose.onNodeWithText(compose.activity.getString(R.string.progress_title)).getUnclippedBoundsInRoot()
        // The floating profile pill reserves FloatingStatusReservedEnd at the top-end of a 320dp screen.
        assertTrue("title right edge ${title.right} overlaps profile button", title.right <= 320.dp - FloatingStatusReservedEnd)
    }
}
