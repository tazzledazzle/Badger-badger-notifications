package com.badger.notifications.gateway

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TenantRateLimiterTest {
    @Test
    fun `allows under limit`() {
        val limiter = TenantRateLimiter(permitsPerMinute = 5)
        repeat(5) {
            assertTrue(limiter.tryAcquire("tenant-a"), "burst $it")
        }
    }

    @Test
    fun `blocks over limit`() {
        val limiter = TenantRateLimiter(permitsPerMinute = 2)
        assertTrue(limiter.tryAcquire("tenant-b"))
        assertTrue(limiter.tryAcquire("tenant-b"))
        assertFalse(limiter.tryAcquire("tenant-b"))
    }
}
