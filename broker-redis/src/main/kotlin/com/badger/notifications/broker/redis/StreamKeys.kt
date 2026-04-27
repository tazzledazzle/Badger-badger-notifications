package com.badger.notifications.broker.redis

import com.badger.notifications.broker.NotificationStream

internal object StreamKeys {
    private const val PREFIX = "notifications"

    fun main(stream: NotificationStream): String = "$PREFIX:${stream.name.lowercase()}"

    const val DLQ: String = "$PREFIX:dlq"
}
