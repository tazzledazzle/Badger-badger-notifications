package com.badger.notifications.broker

import com.badger.notifications.model.Channel

enum class NotificationStream {
    EMAIL,
    SMS,
    PUSH,
    ;

    companion object {
        fun fromChannel(channel: Channel): NotificationStream =
            when (channel) {
                Channel.EMAIL -> EMAIL
                Channel.SMS -> SMS
                Channel.PUSH -> PUSH
            }

        fun all(): List<NotificationStream> = entries
    }
}
