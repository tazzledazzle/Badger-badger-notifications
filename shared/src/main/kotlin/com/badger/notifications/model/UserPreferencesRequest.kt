package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferencesRequest(
    val userId: String,
    val tenantId: String,
    val emailEnabled: Boolean = true,
    val smsEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val dndStart: String? = null,
    val dndEnd: String? = null,
    val timezone: String? = null,
)
