package com.badger.notifications.channels

import com.badger.notifications.model.Channel
import com.badger.notifications.model.OutboundDeliveryJob

interface ChannelSender {
    val channel: Channel

    suspend fun send(
        renderedBody: String,
        job: OutboundDeliveryJob,
    ): Result<Unit>
}

class ChannelSenderRegistry(
    senders: List<ChannelSender>,
) {
    private val byChannel: Map<Channel, ChannelSender> = senders.associateBy { it.channel }

    fun sender(channel: Channel): ChannelSender =
        byChannel[channel]
            ?: error("No sender registered for $channel")
}
