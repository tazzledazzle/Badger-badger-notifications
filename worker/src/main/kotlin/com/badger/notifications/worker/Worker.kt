package com.badger.notifications.worker

import com.badger.notifications.broker.NotificationStream
import com.badger.notifications.broker.RawStreamMessage
import com.badger.notifications.broker.redis.RedisStreamBroker
import com.badger.notifications.channels.ChannelSendersFactory
import com.badger.notifications.channels.TemplateRenderer
import com.badger.notifications.model.DeliveryStatus
import com.badger.notifications.model.OutboundDeliveryJob
import com.badger.notifications.model.ScheduledNotificationPayload
import com.badger.notifications.persistence.NotificationEventRepository
import com.badger.notifications.persistence.PersistenceConfig
import com.badger.notifications.persistence.ScheduledJobRepository
import com.badger.notifications.persistence.TemplateRepository
import com.badger.notifications.persistence.UserPreferenceRepository
import com.sun.net.httpserver.HttpServer
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors

private val log = LoggerFactory.getLogger("BadgerWorker")

const val CONSUMER_GROUP = "badger-workers"

fun main(): Unit =
    runBlocking {
        val jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://localhost:5432/badger"
        val jdbcUser = System.getenv("JDBC_USER") ?: "badger"
        val jdbcPassword = System.getenv("JDBC_PASSWORD") ?: "badger"
        val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"
        val consumerId = System.getenv("WORKER_CONSUMER_ID") ?: "worker-1"
        val maxTries = System.getenv("WORKER_MAX_TRIES")?.toIntOrNull() ?: 5
        val metricsPort = System.getenv("WORKER_METRICS_PORT")?.toIntOrNull() ?: 9404

        val dataSource =
            PersistenceConfig(
                jdbcUrl = jdbcUrl,
                username = jdbcUser,
                password = jdbcPassword,
            ).buildDataSource()
        PersistenceConfig.migrate(dataSource)

        val eventsRepo = NotificationEventRepository(dataSource)
        val templatesRepo = TemplateRepository(dataSource)
        val prefsRepo = UserPreferenceRepository(dataSource)
        val scheduledRepo = ScheduledJobRepository(dataSource)

        val broker = RedisStreamBroker(redisUrl)
        broker.ensureConsumerGroup(CONSUMER_GROUP)
        val senders = ChannelSendersFactory.fromEnv()
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        startMetricsHttpServer(registry, metricsPort)

        launch {
            while (isActive) {
                delay(5_000)
                runScheduledJobs(
                    scheduledRepo,
                    eventsRepo,
                    broker,
                    json,
                )
            }
        }

        while (isActive) {
            val messages =
                broker.readGroup(
                    group = CONSUMER_GROUP,
                    consumerName = consumerId,
                    streams = NotificationStream.all(),
                    count = 10,
                    blockMillis = 5000,
                )
            for (msg in messages) {
                try {
                    handleMessage(
                        msg = msg,
                        broker = broker,
                        eventsRepo = eventsRepo,
                        templatesRepo = templatesRepo,
                        prefsRepo = prefsRepo,
                        senders = senders,
                        json = json,
                        maxTries = maxTries,
                        registry = registry,
                    )
                    broker.acknowledge(CONSUMER_GROUP, msg.stream, msg.messageId)
                } catch (e: Exception) {
                    log.error("Unhandled worker error for {}", msg.messageId, e)
                }
            }
        }
    }

private fun startMetricsHttpServer(
    registry: PrometheusMeterRegistry,
    port: Int,
) {
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext("/metrics") { exchange ->
        val bytes = registry.scrape().toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; version=0.0.4")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.executor = Executors.newSingleThreadExecutor { r -> Thread(r, "badger-worker-metrics") }
    server.start()
    log.info("Worker metrics on http://0.0.0.0:{}/metrics", port)
}

private suspend fun runScheduledJobs(
    scheduledRepo: ScheduledJobRepository,
    eventsRepo: NotificationEventRepository,
    broker: RedisStreamBroker,
    json: Json,
) {
    val due = withContext(Dispatchers.IO) { scheduledRepo.claimDue(100) }
    for (row in due) {
        try {
            val payload = json.decodeFromString<ScheduledNotificationPayload>(row.payloadJson)
            val idempotencyKey = "sched-${row.id}"
            val existing = withContext(Dispatchers.IO) { eventsRepo.findByIdempotencyKey(idempotencyKey) }
            if (existing != null) {
                continue
            }
            val eventId =
                withContext(Dispatchers.IO) {
                    eventsRepo.insertPending(
                        tenantId = row.tenantId,
                        userId = payload.userId,
                        channel = payload.channel,
                        notificationKind = payload.notificationKind,
                        templateId = payload.templateId,
                        variables = payload.variables,
                        idempotencyKey = idempotencyKey,
                        variantTag = null,
                        fallbackChannel = null,
                    )
                }
            val job =
                OutboundDeliveryJob(
                    eventId = eventId,
                    tenantId = row.tenantId,
                    userId = payload.userId,
                    channel = payload.channel,
                    templateId = payload.templateId,
                    variables = payload.variables,
                    notificationKind = payload.notificationKind,
                )
            val stream = NotificationStream.fromChannel(payload.channel)
            broker.publish(stream, json.encodeToString(OutboundDeliveryJob.serializer(), job))
        } catch (e: Exception) {
            log.error("Failed scheduled job {}", row.id, e)
        }
    }
}

private suspend fun handleMessage(
    msg: RawStreamMessage,
    broker: RedisStreamBroker,
    eventsRepo: NotificationEventRepository,
    templatesRepo: TemplateRepository,
    prefsRepo: UserPreferenceRepository,
    senders: com.badger.notifications.channels.ChannelSenderRegistry,
    json: Json,
    maxTries: Int,
    registry: PrometheusMeterRegistry,
) {
    val job = json.decodeFromString<OutboundDeliveryJob>(msg.body)
    val event = eventsRepo.findById(job.eventId) ?: return

    when (event.status) {
        DeliveryStatus.SENT,
        DeliveryStatus.SKIPPED,
        DeliveryStatus.SUPPRESSED,
        DeliveryStatus.DEAD,
        -> return
        else -> {}
    }

    if (event.tries >= maxTries) {
        eventsRepo.updateStatus(job.eventId, DeliveryStatus.DEAD, "max tries exceeded")
        broker.publishToDlq(msg.body)
        return
    }

    if (!prefsRepo.isChannelAllowed(job.userId, job.tenantId, job.channel)) {
        eventsRepo.updateStatus(job.eventId, DeliveryStatus.SUPPRESSED, "preference or DND")
        return
    }

    val templateBody =
        templatesRepo.resolveBody(job.tenantId, job.templateId, job.variantTag)
            ?: "Hello {{user}} — template ${job.templateId}"
    val rendered =
        TemplateRenderer.render(
            templateBody,
            job.variables + mapOf("user" to job.userId),
        )

    val sendResult = senders.sender(job.channel).send(rendered, job)
    if (sendResult.isSuccess) {
        eventsRepo.updateStatus(job.eventId, DeliveryStatus.SENT)
        registry.counter("notifications_sent_total", "channel", job.channel.name).increment()
        return
    }

    val triesAfter = eventsRepo.incrementTries(job.eventId)
    val err = sendResult.exceptionOrNull()?.message
    if (triesAfter >= maxTries) {
        eventsRepo.updateStatus(job.eventId, DeliveryStatus.DEAD, err)
        broker.publishToDlq(msg.body)
        val fb = job.fallbackChannel
        if (fb != null) {
            val fbJob =
                job.copy(
                    channel = fb,
                    fallbackChannel = null,
                )
            broker.publish(
                NotificationStream.fromChannel(fb),
                json.encodeToString(OutboundDeliveryJob.serializer(), fbJob),
            )
        }
        registry.counter("notifications_failed_total", "channel", job.channel.name).increment()
    } else {
        eventsRepo.updateStatus(job.eventId, DeliveryStatus.PENDING, err)
        broker.republish(NotificationStream.fromChannel(job.channel), msg.body)
    }
}
