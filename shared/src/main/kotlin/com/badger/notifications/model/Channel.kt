package com.badger.notifications.model

import kotlinx.serialization.Serializable

@Serializable
enum class Channel {
    EMAIL,
    SMS,
    PUSH,
}
