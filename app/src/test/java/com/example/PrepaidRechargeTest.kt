package com.example

import com.example.domain.PrepaidRecharge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepaidRechargeTest {

    @Test
    fun testNeedsQuery_oncePerPrepaidCycle() {
        assertTrue(
            PrepaidRecharge.needsQuery(
                planType = "PREPAID_35",
                checkedCycleStartMs = null,
                purchaseTimestamp = 1000L
            )
        )
        assertFalse(
            PrepaidRecharge.needsQuery(
                planType = "PREPAID_35",
                checkedCycleStartMs = 1000L,
                purchaseTimestamp = 1000L
            )
        )
        // A new purchase changes the cycle anchor, so the query runs again.
        assertTrue(
            PrepaidRecharge.needsQuery(
                planType = "PREPAID_35",
                checkedCycleStartMs = 1000L,
                purchaseTimestamp = 2000L
            )
        )
    }

    @Test
    fun testNeedsQuery_ignoresMonthlyLine() {
        assertFalse(
            PrepaidRecharge.needsQuery(
                planType = "MONTHLY",
                checkedCycleStartMs = null,
                purchaseTimestamp = 1000L
            )
        )
    }
}
