package com.badger.notifications.channels

import com.badger.notifications.model.Channel
import com.badger.notifications.model.OutboundDeliveryJob
import org.slf4j.LoggerFactory

class ConsoleChannelSender(
    override val channel: Channel,
) : ChannelSender {
    private val log = LoggerFactory.getLogger(ConsoleChannelSender::class.java)

    override suspend fun send(
        renderedBody: String,
        job: OutboundDeliveryJob,
    ): Result<Unit> {
        log.info("[{}] tenant={} user={} event={} body={}", channel, job.tenantId, job.userId, job.eventId, renderedBody)
        return Result.success(Unit)
    }
}
