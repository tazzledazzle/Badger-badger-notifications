package com.badger.notifications.channels

import com.badger.notifications.model.Channel

object ChannelSendersFactory {
    fun fromEnv(): ChannelSenderRegistry {
        val smtp =
            SmtpEmailSender(
                host = System.getenv("SMTP_HOST"),
                port = System.getenv("SMTP_PORT")?.toIntOrNull() ?: 587,
                username = System.getenv("SMTP_USERNAME"),
                password = System.getenv("SMTP_PASSWORD"),
                fromAddress = System.getenv("SMTP_FROM") ?: "no-reply@example.com",
            )
        val twilio =
            TwilioSmsSender(
                accountSid = System.getenv("TWILIO_ACCOUNT_SID"),
                authToken = System.getenv("TWILIO_AUTH_TOKEN"),
                fromNumber = System.getenv("TWILIO_FROM_NUMBER"),
            )
        val fcm =
            FcmPushSender(
                projectId = System.getenv("FCM_PROJECT_ID"),
                accessToken = System.getenv("FCM_ACCESS_TOKEN"),
            )

        val emailSender: ChannelSender =
            if (!System.getenv("SMTP_HOST").isNullOrBlank()) smtp else ConsoleChannelSender(Channel.EMAIL)
        val smsSender: ChannelSender =
            if (!System.getenv("TWILIO_ACCOUNT_SID").isNullOrBlank()) twilio else ConsoleChannelSender(Channel.SMS)
        val pushSender: ChannelSender =
            if (!System.getenv("FCM_PROJECT_ID").isNullOrBlank() && !System.getenv("FCM_ACCESS_TOKEN").isNullOrBlank()) {
                fcm
            } else {
                ConsoleChannelSender(Channel.PUSH)
            }

        return ChannelSenderRegistry(listOf(emailSender, smsSender, pushSender))
    }
}
