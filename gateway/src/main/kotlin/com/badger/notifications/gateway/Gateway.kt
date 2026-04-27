package com.badger.notifications.gateway

import com.badger.notifications.broker.NotificationStream
import com.badger.notifications.broker.redis.RedisStreamBroker
import com.badger.notifications.model.AddTemplateVersionRequest
import com.badger.notifications.model.CreateTemplateRequest
import com.badger.notifications.model.DeliveryStatus
import com.badger.notifications.model.NotifyRequest
import com.badger.notifications.model.NotifyResponse
import com.badger.notifications.model.OutboundDeliveryJob
import com.badger.notifications.model.ScheduleNotifyRequest
import com.badger.notifications.model.ScheduleNotifyResponse
import com.badger.notifications.model.ScheduledNotificationPayload
import com.badger.notifications.model.UserPreferencesRequest
import com.badger.notifications.persistence.NotificationEventRepository
import com.badger.notifications.persistence.PersistenceConfig
import com.badger.notifications.persistence.ScheduledJobRepository
import com.badger.notifications.persistence.TemplateRepository
import com.badger.notifications.persistence.UserPreferenceRepository
import com.badger.notifications.persistence.UserPreferenceRow
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.application.ApplicationCall
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

fun main() {
    val jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://localhost:5432/badger"
    val jdbcUser = System.getenv("JDBC_USER") ?: "badger"
    val jdbcPassword = System.getenv("JDBC_PASSWORD") ?: "badger"
    val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"
    val apiKey = System.getenv("GATEWAY_API_KEY") ?: "dev-key"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val rateLimit = System.getenv("RATE_LIMIT_PER_MINUTE")?.toIntOrNull() ?: 600

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
    val rateLimiter = TenantRateLimiter(rateLimit)
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val enqueueCounter = registry.counter("notifications_enqueued_total")

    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(json)
        }
        install(CallLogging)
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (cause.message ?: "internal error")),
                )
            }
        }
        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
            get("/metrics") {
                call.respondText(registry.scrape(), io.ktor.http.ContentType.parse("text/plain; version=0.0.4"))
            }

            route("/v1") {
                post("/notify") {
                    if (!call.requireApiKey(apiKey)) return@post
                    val req = call.receive<NotifyRequest>()
                    if (!rateLimiter.tryAcquire(req.tenantId)) {
                        call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "rate limit exceeded"))
                        return@post
                    }
                    val idemHeader = call.request.headers["X-Idempotency-Key"]
                    val idempotencyKey = idemHeader ?: req.idempotencyKey
                    if (!idempotencyKey.isNullOrBlank()) {
                        val existing = eventsRepo.findByIdempotencyKey(idempotencyKey)
                        if (existing != null) {
                            call.respond(
                                HttpStatusCode.OK,
                                NotifyResponse(existing.eventId, existing.status, deduplicated = true),
                            )
                            return@post
                        }
                    }
                    val eventId =
                        eventsRepo.insertPending(
                            tenantId = req.tenantId,
                            userId = req.userId,
                            channel = req.channel,
                            notificationKind = req.notificationKind,
                            templateId = req.templateId,
                            variables = req.variables,
                            idempotencyKey = idempotencyKey,
                            variantTag = req.variantTag,
                            fallbackChannel = req.fallbackChannel,
                        )
                    val job =
                        OutboundDeliveryJob(
                            eventId = eventId,
                            tenantId = req.tenantId,
                            userId = req.userId,
                            channel = req.channel,
                            templateId = req.templateId,
                            variables = req.variables,
                            notificationKind = req.notificationKind,
                            variantTag = req.variantTag,
                            fallbackChannel = req.fallbackChannel,
                        )
                    val stream = NotificationStream.fromChannel(req.channel)
                    broker.publish(stream, json.encodeToString(OutboundDeliveryJob.serializer(), job))
                    enqueueCounter.increment()
                    call.respond(HttpStatusCode.Accepted, NotifyResponse(eventId, DeliveryStatus.PENDING))
                }

                get("/events/{id}") {
                    if (!call.requireApiKey(apiKey)) return@get
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val row = eventsRepo.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(row)
                }

                route("/admin/templates") {
                    post {
                        if (!call.requireApiKey(apiKey)) return@post
                        val body = call.receive<CreateTemplateRequest>()
                        val created = templatesRepo.createTemplate(body.tenantId, body.name, body.channel, body.body, body.variantTag)
                        call.respond(HttpStatusCode.Created, created)
                    }
                    get {
                        if (!call.requireApiKey(apiKey)) return@get
                        val tenantId = call.request.queryParameters["tenantId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        call.respond(templatesRepo.listTemplates(tenantId))
                    }
                    get("/{id}") {
                        if (!call.requireApiKey(apiKey)) return@get
                        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val tenantId = call.request.queryParameters["tenantId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val tpl = templatesRepo.getTemplate(id, tenantId) ?: return@get call.respond(HttpStatusCode.NotFound)
                        call.respond(tpl)
                    }
                    post("/{id}/versions") {
                        if (!call.requireApiKey(apiKey)) return@post
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val body = call.receive<AddTemplateVersionRequest>()
                        val v = templatesRepo.addVersion(id, body.body, body.variantTag)
                        call.respond(HttpStatusCode.Created, v)
                    }
                }

                post("/preferences") {
                    if (!call.requireApiKey(apiKey)) return@post
                    val body = call.receive<UserPreferencesRequest>()
                    val zone = body.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                    val dndStart = body.dndStart?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                    val dndEnd = body.dndEnd?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                    prefsRepo.upsert(
                        UserPreferenceRow(
                            userId = body.userId,
                            tenantId = body.tenantId,
                            emailEnabled = body.emailEnabled,
                            smsEnabled = body.smsEnabled,
                            pushEnabled = body.pushEnabled,
                            dndStart = dndStart,
                            dndEnd = dndEnd,
                            timezone = zone,
                        ),
                    )
                    call.respond(HttpStatusCode.NoContent)
                }

                post("/schedule") {
                    if (!call.requireApiKey(apiKey)) return@post
                    val body = call.receive<ScheduleNotifyRequest>()
                    val runAt =
                        try {
                            Instant.parse(body.runAtIso)
                        } catch (_: DateTimeParseException) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid runAtIso"))
                            return@post
                        }
                    val payload =
                        ScheduledNotificationPayload(
                            userId = body.userId,
                            templateId = body.templateId,
                            channel = body.channel,
                            notificationKind = body.notificationKind,
                            variables = body.variables,
                        )
                    val id = scheduledRepo.enqueue(body.tenantId, payload, runAt)
                    call.respond(HttpStatusCode.Accepted, ScheduleNotifyResponse(id))
                }
            }
        }
    }.start(wait = true)
}

private suspend fun ApplicationCall.requireApiKey(expected: String): Boolean {
    if (request.headers["X-API-Key"] != expected) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
        return false
    }
    return true
}
