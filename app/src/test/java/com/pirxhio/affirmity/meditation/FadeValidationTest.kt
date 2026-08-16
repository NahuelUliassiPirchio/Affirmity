package com.pirxhio.affirmity.meditation

import org.junit.Test

/**
 * [Fade]'s `init` block is the one piece of real logic in [MeditationCommand.kt]'s otherwise
 * purely-structural set of new cases — everything else is a plain data holder. This pins its two
 * validation rules directly.
 */
class FadeValidationTest {

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a toVolume outside 0f to 1f`() {
        Fade(FadeTarget.All, toVolume = 1.5f, durationMillis = 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a negative durationMillis`() {
        Fade(FadeTarget.All, toVolume = 0.5f, durationMillis = -1)
    }
}
