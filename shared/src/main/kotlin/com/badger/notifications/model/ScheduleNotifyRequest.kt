package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleNotifyRequest(
    val tenantId: String,
    val runAtIso: String,
    val userId: String,
    val templateId: String,
    val channel: Channel,
    val notificationKind: NotificationKind = NotificationKind.BULK,
    val variables: Map<String, String> = emptyMap(),
)

@Serializable
data class ScheduleNotifyResponse(
    val jobId: String,
)
