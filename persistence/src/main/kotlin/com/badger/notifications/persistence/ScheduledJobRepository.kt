package com.badger.notifications.persistence

import com.badger.notifications.model.ScheduledNotificationPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.postgresql.util.PGobject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

data class ScheduledJobRow(
    val id: String,
    val tenantId: String,
    val payloadJson: String,
    val runAt: Instant,
    val status: String,
)

class ScheduledJobRepository(
    private val dataSource: DataSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun enqueue(
        tenantId: String,
        payload: ScheduledNotificationPayload,
        runAt: Instant,
    ): String {
        val id = UUID.randomUUID().toString()
        val payloadStr = json.encodeToString(payload)
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    INSERT INTO scheduled_jobs (id, tenant_id, payload, run_at, status)
                    VALUES (?, ?, CAST(? AS jsonb), ?, 'PENDING')
                    """.trimIndent(),
                ).apply {
                    setString(1, id)
                    setString(2, tenantId)
                    setObject(3, pgJson(payloadStr))
                    setTimestamp(4, Timestamp.from(runAt))
                }.executeUpdate()
        }
        return id
    }

    fun claimDue(limit: Int): List<ScheduledJobRow> =
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val ids =
                    conn
                        .prepareStatement(
                            """
                            SELECT id FROM scheduled_jobs
                            WHERE status = 'PENDING' AND run_at <= now()
                            ORDER BY run_at ASC
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                            """.trimIndent(),
                        ).apply {
                            setInt(1, limit)
                        }.executeQuery()
                        .use { rs ->
                            buildList {
                                while (rs.next()) {
                                    add(rs.getString("id"))
                                }
                            }
                        }
                if (ids.isEmpty()) {
                    conn.commit()
                    return@use emptyList()
                }
                val mark =
                    conn.prepareStatement(
                        "UPDATE scheduled_jobs SET status = 'ENQUEUED' WHERE id = ?",
                    )
                ids.forEach { jid ->
                    mark.apply {
                        setString(1, jid)
                        addBatch()
                    }
                }
                mark.executeBatch()

                val placeholders = ids.joinToString(",") { "?" }
                val rows =
                    conn
                        .prepareStatement(
                            """
                            SELECT id, tenant_id, payload::text, run_at, status
                            FROM scheduled_jobs WHERE id IN ($placeholders)
                            """.trimIndent(),
                        ).apply {
                            ids.forEachIndexed { index, id -> setString(index + 1, id) }
                        }.executeQuery()
                        .use { rs ->
                            buildList {
                                while (rs.next()) {
                                    add(
                                        ScheduledJobRow(
                                            id = rs.getString("id"),
                                            tenantId = rs.getString("tenant_id"),
                                            payloadJson = rs.getString("payload"),
                                            runAt = rs.getTimestamp("run_at").toInstant(),
                                            status = rs.getString("status"),
                                        ),
                                    )
                                }
                            }
                        }
                conn.commit()
                rows
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }

    private fun pgJson(jsonString: String): PGobject =
        PGobject().apply {
            type = "jsonb"
            value = jsonString
        }
}
