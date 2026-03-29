package de.drremote.trotecsl400.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MatrixSyncClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    data class IncomingMessage(
        val roomId: String,
        val sender: String?,
        val body: String
    )

    suspend fun whoAmI(config: MatrixConfig): String {
        val base = config.homeserverBaseUrl.trim().removeSuffix("/")
        val baseUrl = base.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Homeserver URL is invalid")

        val url = baseUrl.newBuilder()
            .addPathSegments("_matrix/client/v3/account/whoami")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.accessToken}")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.take(500) ?: resp.message
                    throw IllegalStateException("Matrix error ${resp.code}: $msg")
                }
                val body = resp.body?.string() ?: "{}"
                val json = JSONObject(body)
                json.getString("user_id")
            }
        }
    }

    suspend fun syncLoop(
        config: MatrixConfig,
        commandRoomId: String,
        ignoreUserId: String?,
        initialSince: String?,
        onNextBatch: suspend (String) -> Unit,
        onMessage: suspend (IncomingMessage) -> Unit
    ) {
        var since: String? = initialSince

        while (kotlin.coroutines.coroutineContext.isActive) {
            val base = config.homeserverBaseUrl.trim().removeSuffix("/")
            val baseUrl = base.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Homeserver URL is invalid")

            val urlBuilder = baseUrl.newBuilder()
                .addPathSegments("_matrix/client/v3/sync")
                .addQueryParameter("timeout", "30000")

            if (!since.isNullOrBlank()) {
                urlBuilder.addQueryParameter("since", since)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer ${config.accessToken}")
                .get()
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = resp.body?.string()?.take(500) ?: resp.message
                        throw IllegalStateException("Matrix error ${resp.code}: $msg")
                    }
                    resp.body?.string() ?: "{}"
                }
            }

            val json = JSONObject(responseBody)
            since = json.optString("next_batch", since)
            if (!since.isNullOrBlank()) {
                onNextBatch(since)
            }

            val rooms = json.optJSONObject("rooms") ?: JSONObject()
            val join = rooms.optJSONObject("join") ?: JSONObject()
            val roomIds = join.keys()
            while (roomIds.hasNext()) {
                val roomId = roomIds.next()
                if (commandRoomId.isNotBlank() && roomId != commandRoomId) continue
                val room = join.optJSONObject(roomId) ?: continue
                val timeline = room.optJSONObject("timeline") ?: continue
                val events = timeline.optJSONArray("events") ?: continue

                for (i in 0 until events.length()) {
                    val ev = events.optJSONObject(i) ?: continue
                    val type = ev.optString("type", "")
                    if (type != "m.room.message") continue
                    val sender = ev.optString("sender", null)
                    if (ignoreUserId != null && sender == ignoreUserId) continue

                    val content = ev.optJSONObject("content") ?: continue
                    val msgType = content.optString("msgtype", "")
                    if (msgType != "m.text") continue
                    val body = content.optString("body", "")
                    if (body.isBlank()) continue

                    onMessage(IncomingMessage(roomId, sender, body))
                }
            }

            delay(250)
        }
    }
}
