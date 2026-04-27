package com.badger.notifications.persistence

import com.badger.notifications.model.Channel
import com.badger.notifications.model.DeliveryStatus
import com.badger.notifications.model.NotificationKind
import kotlinx.serialization.Serializable

@Serializable
data class NotificationEventRow(
    val eventId: String,
    val idempotencyKey: String?,
    val tenantId: String,
    val userId: String,
    val channel: Channel,
    val notificationKind: NotificationKind,
    val templateId: String,
    val payloadJson: String,
    val status: DeliveryStatus,
    val tries: Int,
    val lastError: String?,
    val variantTag: String?,
    val fallbackChannel: Channel?,
)
