package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
data class ScheduledNotificationPayload(
    val userId: String,
    val templateId: String,
    val channel: Channel,
    val notificationKind: NotificationKind = NotificationKind.BULK,
    val variables: Map<String, String> = emptyMap(),
)
