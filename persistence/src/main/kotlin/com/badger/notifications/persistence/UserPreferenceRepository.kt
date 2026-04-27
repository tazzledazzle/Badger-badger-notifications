package com.badger.notifications.persistence

import com.badger.notifications.model.Channel
import java.sql.Time
import java.sql.Types
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.sql.DataSource

data class UserPreferenceRow(
    val userId: String,
    val tenantId: String,
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val pushEnabled: Boolean,
    val dndStart: LocalTime?,
    val dndEnd: LocalTime?,
    val timezone: ZoneId?,
)

class UserPreferenceRepository(
    private val dataSource: DataSource,
) {
    fun upsert(prefs: UserPreferenceRow) {
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    INSERT INTO user_preferences (
                        user_id, tenant_id, email_enabled, sms_enabled, push_enabled,
                        dnd_start_time, dnd_end_time, timezone
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, tenant_id) DO UPDATE SET
                        email_enabled = EXCLUDED.email_enabled,
                        sms_enabled = EXCLUDED.sms_enabled,
                        push_enabled = EXCLUDED.push_enabled,
                        dnd_start_time = EXCLUDED.dnd_start_time,
                        dnd_end_time = EXCLUDED.dnd_end_time,
                        timezone = EXCLUDED.timezone
                    """.trimIndent(),
                ).apply {
                    var i = 1
                    setString(i++, prefs.userId)
                    setString(i++, prefs.tenantId)
                    setBoolean(i++, prefs.emailEnabled)
                    setBoolean(i++, prefs.smsEnabled)
                    setBoolean(i++, prefs.pushEnabled)
                    if (prefs.dndStart != null) setTime(i++, Time.valueOf(prefs.dndStart)) else setNull(i++, Types.TIME)
                    if (prefs.dndEnd != null) setTime(i++, Time.valueOf(prefs.dndEnd)) else setNull(i++, Types.TIME)
                    setString(i++, prefs.timezone?.id)
                }.executeUpdate()
        }
    }

    fun find(
        userId: String,
        tenantId: String,
    ): UserPreferenceRow? =
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    SELECT user_id, tenant_id, email_enabled, sms_enabled, push_enabled,
                           dnd_start_time, dnd_end_time, timezone
                    FROM user_preferences
                    WHERE user_id = ? AND tenant_id = ?
                    """.trimIndent(),
                ).apply {
                    setString(1, userId)
                    setString(2, tenantId)
                }.executeQuery()
                .use { rs ->
                    if (!rs.next()) return@use null
                    UserPreferenceRow(
                        userId = rs.getString("user_id"),
                        tenantId = rs.getString("tenant_id"),
                        emailEnabled = rs.getBoolean("email_enabled"),
                        smsEnabled = rs.getBoolean("sms_enabled"),
                        pushEnabled = rs.getBoolean("push_enabled"),
                        dndStart = rs.getTime("dnd_start_time")?.toLocalTime(),
                        dndEnd = rs.getTime("dnd_end_time")?.toLocalTime(),
                        timezone =
                            rs.getString("timezone")?.takeIf { it.isNotBlank() }?.let {
                                ZoneId.of(it)
                            },
                    )
                }
        }

    fun isChannelAllowed(
        userId: String,
        tenantId: String,
        channel: Channel,
    ): Boolean {
        val row = find(userId, tenantId) ?: return true
        if (isInDnd(row)) return false
        return when (channel) {
            Channel.EMAIL -> row.emailEnabled
            Channel.SMS -> row.smsEnabled
            Channel.PUSH -> row.pushEnabled
        }
    }

    private fun isInDnd(row: UserPreferenceRow): Boolean {
        val start = row.dndStart ?: return false
        val end = row.dndEnd ?: return false
        val zone = row.timezone ?: ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone).toLocalTime()
        return if (start == end) {
            false
        } else if (start.isBefore(end)) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
    }
}
