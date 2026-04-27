package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD,
    SKIPPED,
    SUPPRESSED,
}
