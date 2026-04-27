package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
data class OutboundDeliveryJob(
    val eventId: String,
    val tenantId: String,
    val userId: String,
    val channel: Channel,
    val templateId: String,
    val variables: Map<String, String> = emptyMap(),
    val notificationKind: NotificationKind = NotificationKind.TRANSACTIONAL,
    val variantTag: String? = null,
    val fallbackChannel: Channel? = null,
    val enqueueAttempt: Int = 0,
)
