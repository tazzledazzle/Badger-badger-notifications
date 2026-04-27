package com.badger.notifications.broker.redis

import com.badger.notifications.broker.BrokerConsumer
import com.badger.notifications.broker.BrokerPublisher
import com.badger.notifications.broker.NotificationStream
import com.badger.notifications.broker.RawStreamMessage
import io.lettuce.core.Consumer
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class RedisStreamBroker(
    redisUrl: String,
) : BrokerPublisher, BrokerConsumer {
    private val client: RedisClient = RedisClient.create(RedisURI.create(redisUrl))
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val sync: RedisCommands<String, String> = connection.sync()

    fun close() {
        connection.close()
        client.shutdown()
    }

    fun ensureConsumerGroup(
        group: String,
        streams: List<NotificationStream> = NotificationStream.all(),
    ) {
        for (stream in streams) {
            val key = StreamKeys.main(stream)
            try {
                sync.xgroupCreate(
                    XReadArgs.StreamOffset.from(key, "0"),
                    group,
                    XGroupCreateArgs.Builder.mkstream(),
                )
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (!msg.contains("BUSYGROUP", ignoreCase = true)) {
                    throw e
                }
            }
        }
    }

    override suspend fun publish(stream: NotificationStream, body: String): String =
        withContext(Dispatchers.IO) {
            val key = StreamKeys.main(stream)
            sync.xadd(key, mapOf("payload" to body))
        }

    override suspend fun readGroup(
        group: String,
        consumerName: String,
        streams: List<NotificationStream>,
        count: Long,
        blockMillis: Long,
    ): List<RawStreamMessage> =
        withContext(Dispatchers.IO) {
            if (streams.isEmpty()) return@withContext emptyList()
            val offsets =
                streams.map { s ->
                    XReadArgs.StreamOffset.from(StreamKeys.main(s), ">")
                }.toTypedArray()
            val readArgs =
                XReadArgs.Builder
                    .block(Duration.ofMillis(blockMillis))
                    .count(count)
            val result =
                sync.xreadgroup(
                    Consumer.from(group, consumerName),
                    readArgs,
                    *offsets,
                ) ?: emptyList()
            result.map { msg ->
                val stream =
                    streams.first { StreamKeys.main(it) == msg.stream }
                val body = msg.body["payload"] ?: msg.body.values.firstOrNull().orEmpty()
                RawStreamMessage(stream, msg.id, body)
            }
        }

    override suspend fun acknowledge(group: String, stream: NotificationStream, messageId: String) {
        withContext(Dispatchers.IO) {
            val key = StreamKeys.main(stream)
            sync.xack(key, group, messageId)
        }
    }

    override suspend fun publishToDlq(body: String): String =
        withContext(Dispatchers.IO) {
            sync.xadd(StreamKeys.DLQ, mapOf("payload" to body))
        }

    override suspend fun republish(stream: NotificationStream, body: String): String = publish(stream, body)
}
