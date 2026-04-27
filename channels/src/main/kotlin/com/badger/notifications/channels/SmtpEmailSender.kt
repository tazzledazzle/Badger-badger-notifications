package com.badger.notifications.channels

import com.badger.notifications.model.Channel
import com.badger.notifications.model.OutboundDeliveryJob
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class SmtpEmailSender(
    private val host: String?,
    private val port: Int,
    private val username: String?,
    private val password: String?,
    private val fromAddress: String,
    private val useTls: Boolean = true,
) : ChannelSender {
    override val channel: Channel = Channel.EMAIL

    override suspend fun send(
        renderedBody: String,
        job: OutboundDeliveryJob,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (host.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("SMTP_HOST not configured"))
            }
            val to = job.variables["to"] ?: return@withContext Result.failure(IllegalArgumentException("variables.to required for email"))
            val subject = job.variables["subject"] ?: "Notification"

            val props =
                Properties().apply {
                    put("mail.smtp.host", host)
                    put("mail.smtp.port", port.toString())
                    put("mail.smtp.auth", (username != null).toString())
                    if (useTls) {
                        put("mail.smtp.starttls.enable", "true")
                    }
                }
            val session =
                Session.getInstance(props, null)
            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(fromAddress))
                    setRecipient(Message.RecipientType.TO, InternetAddress(to))
                    setSubject(subject)
                    setText(renderedBody)
                }
            if (username != null && password != null) {
                Transport.send(message, username, password)
            } else {
                Transport.send(message)
            }
            Result.success(Unit)
        }
}
