package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateTemplateRequest(
    val tenantId: String,
    val name: String,
    val channel: Channel,
    val body: String,
    val variantTag: String? = null,
)

@Serializable
data class AddTemplateVersionRequest(
    val body: String,
    val variantTag: String? = null,
)
