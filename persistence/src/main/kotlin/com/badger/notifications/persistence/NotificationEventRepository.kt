package com.badger.notifications.persistence

import com.badger.notifications.model.Channel
import com.badger.notifications.model.DeliveryStatus
import com.badger.notifications.model.NotificationKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class NotificationEventRepository(
    private val dataSource: DataSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun findByIdempotencyKey(key: String): NotificationEventRow? =
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    SELECT event_id, idempotency_key, tenant_id, user_id, channel, notification_kind,
                           template_id, payload::text, status, tries, last_error, variant_tag, fallback_channel
                    FROM notification_events
                    WHERE idempotency_key = ?
                    """.trimIndent(),
                ).apply {
                    setString(1, key)
                }.executeQuery()
                .use { rs ->
                    if (rs.next()) rs.readRow() else null
                }
        }

    fun findById(eventId: String): NotificationEventRow? =
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    SELECT event_id, idempotency_key, tenant_id, user_id, channel, notification_kind,
                           template_id, payload::text, status, tries, last_error, variant_tag, fallback_channel
                    FROM notification_events
                    WHERE event_id = ?
                    """.trimIndent(),
                ).apply {
                    setString(1, eventId)
                }.executeQuery()
                .use { rs ->
                    if (rs.next()) rs.readRow() else null
                }
        }

    fun insertPending(
        tenantId: String,
        userId: String,
        channel: Channel,
        notificationKind: NotificationKind,
        templateId: String,
        variables: Map<String, String>,
        idempotencyKey: String?,
        variantTag: String?,
        fallbackChannel: Channel?,
    ): String {
        val eventId = UUID.randomUUID().toString()
        val payload =
            json.encodeToString(
                mapOf("variables" to variables),
            )
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    INSERT INTO notification_events (
                        event_id, idempotency_key, tenant_id, user_id, channel, notification_kind,
                        template_id, payload, status, variant_tag, fallback_channel
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                    """.trimIndent(),
                ).apply {
                    var i = 1
                    setString(i++, eventId)
                    if (idempotencyKey != null) setString(i++, idempotencyKey) else setNull(i++, Types.VARCHAR)
                    setString(i++, tenantId)
                    setString(i++, userId)
                    setString(i++, channel.name)
                    setString(i++, notificationKind.name)
                    setString(i++, templateId)
                    setObject(i++, pgJson(payload))
                    setString(i++, DeliveryStatus.PENDING.name)
                    if (variantTag != null) setString(i++, variantTag) else setNull(i++, Types.VARCHAR)
                    if (fallbackChannel != null) setString(i++, fallbackChannel.name) else setNull(i++, Types.VARCHAR)
                }.executeUpdate()
        }
        return eventId
    }

    fun updateStatus(
        eventId: String,
        status: DeliveryStatus,
        lastError: String? = null,
    ) {
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    UPDATE notification_events
                    SET status = ?, last_error = ?, updated_at = ?
                    WHERE event_id = ?
                    """.trimIndent(),
                ).apply {
                    var i = 1
                    setString(i++, status.name)
                    setString(i++, lastError)
                    setTimestamp(i++, Timestamp.from(Instant.now()))
                    setString(i++, eventId)
                }.executeUpdate()
        }
    }

    fun incrementTries(eventId: String): Int {
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    UPDATE notification_events
                    SET tries = tries + 1, updated_at = ?
                    WHERE event_id = ?
                    RETURNING tries
                    """.trimIndent(),
                ).apply {
                    setTimestamp(1, Timestamp.from(Instant.now()))
                    setString(2, eventId)
                }.executeQuery()
                .use { rs ->
                    require(rs.next()) { "event not found" }
                    return rs.getInt(1)
                }
        }
    }

    private fun ResultSet.readRow(): NotificationEventRow =
        NotificationEventRow(
            eventId = getString("event_id"),
            idempotencyKey = getString("idempotency_key"),
            tenantId = getString("tenant_id"),
            userId = getString("user_id"),
            channel = Channel.valueOf(getString("channel")),
            notificationKind = NotificationKind.valueOf(getString("notification_kind")),
            templateId = getString("template_id"),
            payloadJson = getString("payload"),
            status = DeliveryStatus.valueOf(getString("status")),
            tries = getInt("tries"),
            lastError = getString("last_error"),
            variantTag = getString("variant_tag"),
            fallbackChannel =
                getString("fallback_channel")?.takeIf { it.isNotBlank() }?.let {
                    Channel.valueOf(it)
                },
        )

    private fun pgJson(jsonString: String): PGobject =
        PGobject().apply {
            type = "jsonb"
            value = jsonString
        }
}
