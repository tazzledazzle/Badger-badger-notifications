package com.badger.notifications.channels

import com.badger.notifications.model.Channel
import com.badger.notifications.model.OutboundDeliveryJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class TwilioSmsSender(
    private val accountSid: String?,
    private val authToken: String?,
    private val fromNumber: String?,
    private val client: OkHttpClient = OkHttpClient(),
) : ChannelSender {
    override val channel: Channel = Channel.SMS

    override suspend fun send(
        renderedBody: String,
        job: OutboundDeliveryJob,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (accountSid.isNullOrBlank() || authToken.isNullOrBlank() || fromNumber.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Twilio env not configured"))
            }
            val to = job.variables["to"] ?: return@withContext Result.failure(IllegalArgumentException("variables.to required for SMS"))

            val url = "https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json"
            val form =
                FormBody
                    .Builder()
                    .add("To", to)
                    .add("From", fromNumber)
                    .add("Body", renderedBody)
                    .build()

            val credential = Credentials.basic(accountSid, authToken)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", credential)
                    .post(form)
                    .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Twilio HTTP ${response.code}: ${response.body?.string()}"),
                    )
                }
            }
            Result.success(Unit)
        }
}
