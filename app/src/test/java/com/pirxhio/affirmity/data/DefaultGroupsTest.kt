package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGroupsTest {

    @Test
    fun `registry order is Everyday Essentials then Morning Start`() {
        assertEquals(
            listOf("default.everyday_essentials", "default.morning_start"),
            DefaultGroups.all.map { it.id },
        )
    }

    @Test
    fun `isDefaultGroupId recognises registry ids only`() {
        assertTrue(DefaultGroups.isDefaultGroupId("default.everyday_essentials"))
        assertTrue(DefaultGroups.isDefaultGroupId("default.morning_start"))
        assertFalse(DefaultGroups.isDefaultGroupId("3f2c7c1e-0000-4000-8000-000000000000"))
        assertFalse(DefaultGroups.isDefaultGroupId("default.unknown"))
    }
}
