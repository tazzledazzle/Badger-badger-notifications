package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationKind {
    TRANSACTIONAL,
    BULK,
}
