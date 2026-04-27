package com.badger.notifications.gateway

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class TenantRateLimiter(
    private val permitsPerMinute: Int,
) {
    private val tenants = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun tryAcquire(tenant: String): Boolean {
        val q = tenants.computeIfAbsent(tenant) { ArrayDeque() }
        val now = System.currentTimeMillis()
        synchronized(q) {
            while (q.isNotEmpty() && now - q.first() > 60_000L) {
                q.removeFirst()
            }
            if (q.size >= permitsPerMinute) {
                return false
            }
            q.addLast(now)
            return true
        }
    }
}
