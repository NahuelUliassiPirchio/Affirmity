package com.pirxhio.affirmity.ui.groups

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.isUnlocked
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-first for design D6's two-level facade. `catalogAccessDecision` short-circuits on
 * `alwaysSelected` FIRST, preserving `GroupAccessPolicy`'s "PERSONALIZADAS_GROUP is never locked"
 * regression guard.
 */
class CatalogAccessPolicyTest {

    private val freeGroup = AffirmationGroup(
        id = "free_group",
        titleRes = R.string.affirmation_group_personalizadas_title,
        descriptionRes = R.string.affirmation_group_personalizadas_description,
        icon = Icons.Filled.Favorite,
        access = ContentAccess.Free,
    )

    private val proGroup = freeGroup.copy(id = "pro_group", access = ContentAccess.Pro)

    private fun collection(access: ContentAccess) = CatalogCollection(
        id = "collection",
        universeId = "free_group",
        themeId = "theme",
        access = access,
        order = 1,
    )

    @Test
    fun `alwaysSelected short-circuits first regardless of collection access`() {
        val alwaysSelectedGroup = freeGroup.copy(alwaysSelected = true, access = ContentAccess.Pro)
        val decision = catalogAccessDecision(
            group = alwaysSelectedGroup,
            collection = collection(ContentAccess.Pro),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(AccessDecision.Unlocked, decision)
    }

    @Test
    fun `free collection in a Pro group is locked - D6(a) regression guard`() {
        val decision = catalogAccessDecision(
            group = proGroup,
            collection = collection(ContentAccess.Free),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(false, decision.isUnlocked)
    }

    @Test
    fun `Pro collection in a Free group is locked`() {
        val decision = catalogAccessDecision(
            group = freeGroup,
            collection = collection(ContentAccess.Pro),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(false, decision.isUnlocked)
    }

    @Test
    fun `Free group and Free collection is unlocked`() {
        val decision = catalogAccessDecision(
            group = freeGroup,
            collection = collection(ContentAccess.Free),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(true, decision.isUnlocked)
    }

    @Test
    fun `a null collection contributes Unlocked so the group gate alone decides`() {
        val decisionFreeGroup = catalogAccessDecision(
            group = freeGroup,
            collection = null,
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(true, decisionFreeGroup.isUnlocked)

        val decisionProGroup = catalogAccessDecision(
            group = proGroup,
            collection = null,
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(false, decisionProGroup.isUnlocked)
    }

    @Test
    fun `Pro user unlocks a Pro collection under a Free group`() {
        val decision = catalogAccessDecision(
            group = freeGroup,
            collection = collection(ContentAccess.Pro),
            tier = AccessTier.PRO,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(true, decision.isUnlocked)
    }
}
