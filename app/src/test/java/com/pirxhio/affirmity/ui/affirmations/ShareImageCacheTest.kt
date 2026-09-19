package com.pirxhio.affirmity.ui.affirmations

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareImageCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun prepareDeletesPreviousSharesButKeepsDirectory() {
        val dir = tmp.newFolder("shared_images")
        val old = File(dir, "affirmity_old.png").apply { writeText("x") }
        val target = ShareImageCache(dir).prepare("abc")
        assertFalse(old.exists())
        assertTrue(dir.isDirectory)
        assertEquals(dir, target.parentFile)
        assertEquals("png", target.extension)
    }

    @Test
    fun prepareCreatesMissingDirectory() {
        val dir = File(tmp.root, "cache/shared_images")
        val target = ShareImageCache(dir).prepare("abc")
        assertTrue(target.parentFile!!.isDirectory)
    }

    @Test
    fun prepareSanitizesUnsafeIds() {
        val target = ShareImageCache(tmp.newFolder("d")).prepare("../../evil id/\u0000")
        assertEquals(target.parentFile!!.name, "d")
        assertFalse(target.name.contains("/") || target.name.contains(".."))
    }

    @Test
    fun prepareDoesNotTouchNonPngFiles() {
        val dir = tmp.newFolder("keep")
        val other = File(dir, "notes.txt").apply { writeText("x") }
        ShareImageCache(dir).prepare("a")
        assertTrue(other.exists())
    }
}
