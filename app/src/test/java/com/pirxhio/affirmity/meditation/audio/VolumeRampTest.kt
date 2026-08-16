package com.pirxhio.affirmity.meditation.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRampTest {

    @Test
    fun `interpolates linearly at intermediate progress`() {
        val ramp = VolumeRamp(fromVolume = 0f, toVolume = 1f, durationMillis = 1_000L)

        assertEquals(0.4f, ramp.volumeAt(400L), 0.0001f)
        assertEquals(0.75f, ramp.volumeAt(750L), 0.0001f)
    }

    @Test
    fun `clamps before the ramp starts`() {
        val ramp = VolumeRamp(fromVolume = 0.2f, toVolume = 0.8f, durationMillis = 1_000L)

        assertEquals(0.2f, ramp.volumeAt(-500L), 0.0001f)
    }

    @Test
    fun `clamps once the ramp is past its duration`() {
        val ramp = VolumeRamp(fromVolume = 0.2f, toVolume = 0.8f, durationMillis = 1_000L)

        assertEquals(0.8f, ramp.volumeAt(5_000L), 0.0001f)
        assertEquals(true, ramp.isCompleteAt(5_000L))
    }

    @Test
    fun `a zero-duration ramp returns toVolume immediately and is already complete`() {
        val ramp = VolumeRamp(fromVolume = 1f, toVolume = 0f, durationMillis = 0L)

        assertEquals(0f, ramp.volumeAt(0L), 0.0001f)
        assertEquals(true, ramp.isCompleteAt(0L))
    }

    @Test
    fun `is not complete before its duration elapses`() {
        val ramp = VolumeRamp(fromVolume = 0f, toVolume = 1f, durationMillis = 1_000L)

        assertEquals(false, ramp.isCompleteAt(999L))
    }

    @Test
    fun `resumedFrom freezes the current volume as the new fromVolume and shortens the remaining duration`() {
        val ramp = VolumeRamp(fromVolume = 0f, toVolume = 1f, durationMillis = 1_000L)

        val resumed = ramp.resumedFrom(volumeAtPause = 0.4f, elapsedAtPause = 400L)

        assertEquals(0.4f, resumed.fromVolume, 0.0001f)
        assertEquals(1f, resumed.toVolume, 0.0001f)
        assertEquals(600L, resumed.durationMillis)
    }

    @Test
    fun `resumedFrom never produces a negative remaining duration`() {
        val ramp = VolumeRamp(fromVolume = 0f, toVolume = 1f, durationMillis = 1_000L)

        val resumed = ramp.resumedFrom(volumeAtPause = 1f, elapsedAtPause = 5_000L)

        assertEquals(0L, resumed.durationMillis)
    }
}
