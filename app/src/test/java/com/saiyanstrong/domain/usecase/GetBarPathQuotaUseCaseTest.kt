package com.saiyanstrong.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetBarPathQuotaUseCaseTest {

    @Test
    fun `under the free limit is not exhausted`() {
        val quota = computeBarPathQuota(usedThisMonth = 3, isCoach = false)
        assertEquals(3, quota.usedThisMonth)
        assertEquals(5, quota.limit)
        assertFalse(quota.isUnlimited)
        assertFalse(quota.isExhausted)
    }

    @Test
    fun `at the free limit is exhausted`() {
        val quota = computeBarPathQuota(usedThisMonth = 5, isCoach = false)
        assertTrue(quota.isExhausted)
    }

    @Test
    fun `coach entitlement overrides an exhausted count`() {
        val quota = computeBarPathQuota(usedThisMonth = 9, isCoach = true)
        assertTrue(quota.isUnlimited)
        assertFalse(quota.isExhausted)
    }
}
