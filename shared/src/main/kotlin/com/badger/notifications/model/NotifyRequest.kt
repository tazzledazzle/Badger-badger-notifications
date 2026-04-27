package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
data class NotifyRequest(
    val tenantId: String,
    val userId: String,
    val templateId: String,
    val channel: Channel,
    val notificationKind: NotificationKind = NotificationKind.TRANSACTIONAL,
    val variables: Map<String, String> = emptyMap(),
    val priority: Int = 0,
    val idempotencyKey: String? = null,
    val variantTag: String? = null,
    val fallbackChannel: Channel? = null,
)

@Serializable
data class NotifyResponse(
    val eventId: String,
    val status: DeliveryStatus,
    val deduplicated: Boolean = false,
)
