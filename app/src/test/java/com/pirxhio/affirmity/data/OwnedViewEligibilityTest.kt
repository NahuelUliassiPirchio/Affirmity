package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.*
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnedViewEligibilityTest {
    @Test fun `another owner or unowned archive cannot carry eligibility into new generation`() {
        assertEquals(1, advanceOwnedDailyViews(DailyViewCount(10, 29), "local:H", 10).count)
        val a = advanceOwnedDailyViews(DailyViewCount(10, 29), "account:A", 10)
        assertEquals(2, advanceOwnedDailyViews(a, "account:A", 10).count)
        assertEquals(1, advanceOwnedDailyViews(a, "account:B", 10).count)
        assertEquals(1, advanceOwnedDailyViews(a, "account:A", 11).count)
    }
}
