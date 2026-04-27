package com.badger.notifications.broker

data class RawStreamMessage(
    val stream: NotificationStream,
    val messageId: String,
    val body: String,
)

interface BrokerConsumer {
    suspend fun readGroup(
        group: String,
        consumerName: String,
        streams: List<NotificationStream>,
        count: Long,
        blockMillis: Long,
    ): List<RawStreamMessage>

    suspend fun acknowledge(group: String, stream: NotificationStream, messageId: String)

    suspend fun publishToDlq(body: String): String

    suspend fun republish(stream: NotificationStream, body: String): String
}
