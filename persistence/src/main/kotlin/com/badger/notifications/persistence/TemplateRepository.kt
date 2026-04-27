package com.badger.notifications.persistence

import com.badger.notifications.model.Channel
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.sql.DataSource

@Serializable
data class TemplateRow(
    val id: String,
    val tenantId: String,
    val name: String,
    val channel: Channel,
    val currentVersion: Int,
)

@Serializable
data class TemplateVersionRow(
    val id: String,
    val templateId: String,
    val version: Int,
    val body: String,
    val variantTag: String?,
)

class TemplateRepository(
    private val dataSource: DataSource,
) {
    fun createTemplate(
        tenantId: String,
        name: String,
        channel: Channel,
        body: String,
        variantTag: String?,
    ): TemplateRow {
        val templateId = UUID.randomUUID().toString()
        val versionId = UUID.randomUUID().toString()
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn
                    .prepareStatement(
                        """
                        INSERT INTO templates (id, tenant_id, name, channel, current_version)
                        VALUES (?, ?, ?, ?, 1)
                        """.trimIndent(),
                    ).apply {
                        setString(1, templateId)
                        setString(2, tenantId)
                        setString(3, name)
                        setString(4, channel.name)
                    }.executeUpdate()

                conn
                    .prepareStatement(
                        """
                        INSERT INTO template_versions (id, template_id, version, body, variant_tag)
                        VALUES (?, ?, 1, ?, ?)
                        """.trimIndent(),
                    ).apply {
                        setString(1, versionId)
                        setString(2, templateId)
                        setString(3, body)
                        setString(4, variantTag)
                    }.executeUpdate()

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        return TemplateRow(templateId, tenantId, name, channel, 1)
    }

    fun addVersion(
        templateId: String,
        body: String,
        variantTag: String?,
    ): TemplateVersionRow {
        val versionId = UUID.randomUUID().toString()
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val nextVersion =
                    conn
                        .prepareStatement(
                            "SELECT COALESCE(MAX(version), 0) + 1 AS v FROM template_versions WHERE template_id = ?",
                        ).apply {
                            setString(1, templateId)
                        }.executeQuery()
                        .use { rs ->
                            require(rs.next())
                            rs.getInt("v")
                        }

                conn
                    .prepareStatement(
                        """
                        INSERT INTO template_versions (id, template_id, version, body, variant_tag)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).apply {
                        setString(1, versionId)
                        setString(2, templateId)
                        setInt(3, nextVersion)
                        setString(4, body)
                        setString(5, variantTag)
                    }.executeUpdate()

                conn
                    .prepareStatement(
                        """
                        UPDATE templates SET current_version = ? WHERE id = ?
                        """.trimIndent(),
                    ).apply {
                        setInt(1, nextVersion)
                        setString(2, templateId)
                    }.executeUpdate()

                conn.commit()
                return TemplateVersionRow(versionId, templateId, nextVersion, body, variantTag)
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun listTemplates(tenantId: String): List<TemplateRow> =
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    SELECT id, tenant_id, name, channel, current_version
                    FROM templates WHERE tenant_id = ?
                    """.trimIndent(),
                ).apply {
                    setString(1, tenantId)
                }.executeQuery()
                .use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                TemplateRow(
                                    id = rs.getString("id"),
                                    tenantId = rs.getString("tenant_id"),
                                    name = rs.getString("name"),
                                    channel = Channel.valueOf(rs.getString("channel")),
                                    currentVersion = rs.getInt("current_version"),
                                ),
                            )
                        }
                    }
                }
        }

    fun getTemplate(
        templateId: String,
        tenantId: String,
    ): TemplateRow? =
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                    SELECT id, tenant_id, name, channel, current_version
                    FROM templates WHERE id = ? AND tenant_id = ?
                    """.trimIndent(),
                ).apply {
                    setString(1, templateId)
                    setString(2, tenantId)
                }.executeQuery()
                .use { rs ->
                    if (!rs.next()) return@use null
                    TemplateRow(
                        id = rs.getString("id"),
                        tenantId = rs.getString("tenant_id"),
                        name = rs.getString("name"),
                        channel = Channel.valueOf(rs.getString("channel")),
                        currentVersion = rs.getInt("current_version"),
                    )
                }
        }

    fun resolveBody(
        tenantId: String,
        templateId: String,
        variantTag: String?,
    ): String? =
        dataSource.connection.use { conn ->
            val tpl =
                getTemplate(templateId, tenantId) ?: return@use null
            if (variantTag.isNullOrBlank()) {
                conn
                    .prepareStatement(
                        """
                        SELECT body FROM template_versions
                        WHERE template_id = ? AND version = ?
                        """.trimIndent(),
                    ).apply {
                        setString(1, templateId)
                        setInt(2, tpl.currentVersion)
                    }.executeQuery()
                    .use { rs ->
                        if (rs.next()) rs.getString("body") else null
                    }
            } else {
                conn
                    .prepareStatement(
                        """
                        SELECT body FROM template_versions
                        WHERE template_id = ? AND variant_tag = ?
                        ORDER BY version DESC LIMIT 1
                        """.trimIndent(),
                    ).apply {
                        setString(1, templateId)
                        setString(2, variantTag)
                    }.executeQuery()
                    .use { rs ->
                        if (rs.next()) rs.getString("body") else null
                    }
            }
        }
}
