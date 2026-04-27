package com.badger.notifications.channels

import com.badger.notifications.model.Channel
import com.badger.notifications.model.OutboundDeliveryJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

/**
 * Optional FCM HTTP v1 send when [serverKey] (legacy) or [projectId] + [accessToken] are provided.
 * If unset, logs only (safe for local dev).
 */
class FcmPushSender(
    private val projectId: String?,
    private val accessToken: String?,
    private val client: OkHttpClient = OkHttpClient(),
) : ChannelSender {
    override val channel: Channel = Channel.PUSH

    private val log = LoggerFactory.getLogger(FcmPushSender::class.java)

    override suspend fun send(
        renderedBody: String,
        job: OutboundDeliveryJob,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val token = job.variables["deviceToken"] ?: job.variables["to"]
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("variables.deviceToken (or to) required for push"))
            }
            if (projectId.isNullOrBlank() || accessToken.isNullOrBlank()) {
                log.info("[PUSH stub] event={} tokenPrefix={} body={}", job.eventId, token.take(8), renderedBody)
                return@withContext Result.success(Unit)
            }

            val url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
            val jsonObject =
                buildJsonObject {
                    put(
                        "message",
                        buildJsonObject {
                            put("token", token)
                            put(
                                "notification",
                                buildJsonObject {
                                    put("title", "Badger")
                                    put("body", renderedBody)
                                },
                            )
                        },
                    )
                }
            val json = Json.encodeToString(JsonElement.serializer(), jsonObject)

            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("FCM HTTP ${response.code}: ${response.body?.string()}"),
                    )
                }
            }
            Result.success(Unit)
        }
}
