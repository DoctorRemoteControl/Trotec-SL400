package de.drremote.trotecsl400.matrix

import de.drremote.trotecsl400.alert.AlertConfig
import de.drremote.trotecsl400.alert.MetricMode
import de.drremote.trotecsl400.sl400.AcousticMetrics
import de.drremote.trotecsl400.sl400.Sl400Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.random.Random

class HttpMatrixPublisher(
    private val client: OkHttpClient = OkHttpClient()
) : MatrixPublisher {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun sendSample(config: MatrixConfig, sample: Sl400Sample) {
        val dbText = String.format(Locale.US, "%.1f", sample.db)
        val body = "SL400: $dbText dB (raw=${sample.rawTenths}, aux06=${sample.aux06Hex ?: "-"})"

        val content = JSONObject()
            .put("msgtype", "m.text")
            .put("body", body)
            .put(
                "sl400",
                JSONObject()
                    .put("timestampMs", sample.timestampMs)
                    .put("dbTenths", sample.rawTenths)
                    .put("dbText", dbText)
                    .put("rawTenths", sample.rawTenths)
                    .put("aux06Hex", sample.aux06Hex ?: JSONObject.NULL)
                    .put("tags", JSONArray(sample.tags))
            )

        sendEvent(config, config.roomId, "m.room.message", content)
    }

    override suspend fun sendAlert(
        config: MatrixConfig,
        sample: Sl400Sample,
        metrics: AcousticMetrics,
        alertConfig: AlertConfig,
        audioHint: String?
    ) {
        val triggerValue = metricValue(metrics, alertConfig.metricMode)
        val triggerText = triggerValue?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
        val thresholdText = String.format(Locale.US, "%.1f", alertConfig.thresholdDb)
        val currentText = String.format(Locale.US, "%.1f", metrics.currentDb)
        val max1Text = metrics.maxDb1Min?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
        val label = metricLabel(alertConfig.metricMode)

        val hintText = audioHint?.takeIf { it.isNotBlank() }
        val body = buildString {
            append("SL400 ALERT: $label = $triggerText dB, threshold = $thresholdText dB. ")
            append("Live = $currentText dB, Max 1 min = $max1Text dB.")
            if (hintText != null) {
                append(" Hint: ").append(hintText)
            }
        }

        val content = JSONObject()
            .put("msgtype", "m.text")
            .put("body", body)
            .put(
                "sl400_alert",
                JSONObject()
                    .put("timestampMs", sample.timestampMs)
                    .put("metricMode", alertConfig.metricMode.name)
                    .put("metricLabel", label)
                    .put("metricValue", triggerValue ?: JSONObject.NULL)
                    .put("thresholdDb", alertConfig.thresholdDb)
                    .put("currentDb", metrics.currentDb)
                    .put("maxDb1Min", metrics.maxDb1Min ?: JSONObject.NULL)
                    .put("laEq1Min", metrics.laEq1Min ?: JSONObject.NULL)
                    .put("laEq5Min", metrics.laEq5Min ?: JSONObject.NULL)
                    .put("laEq15Min", metrics.laEq15Min ?: JSONObject.NULL)
                    .put("coverage1MinMs", metrics.coverage1MinMs)
                    .put("coverage5MinMs", metrics.coverage5MinMs)
                    .put("coverage15MinMs", metrics.coverage15MinMs)
                    .put("timeAboveThresholdMs1Min", metrics.timeAboveThresholdMs1Min)
                    .put("audioHint", hintText ?: JSONObject.NULL)
            )

        sendEvent(config, config.roomId, "m.room.message", content)
    }

    override suspend fun uploadMedia(
        config: MatrixConfig,
        file: File,
        mimeType: String,
        fileName: String
    ): MxcUploadResult {
        if (config.accessToken.isBlank()) {
            throw IllegalArgumentException("Access token is missing")
        }

        val base = config.homeserverBaseUrl.trim().removeSuffix("/")
        val baseUrl = base.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Homeserver URL is invalid")

        val url = baseUrl.newBuilder()
            .addPathSegments("_matrix/media/v3/upload")
            .addQueryParameter("filename", fileName)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.accessToken}")
            .post(file.asRequestBody(mimeType.toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.take(500) ?: resp.message
                    throw IllegalStateException("Matrix upload error ${resp.code}: $msg")
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val contentUri = json.optString("content_uri")
                if (contentUri.isBlank()) {
                    throw IllegalStateException("Matrix upload missing content_uri")
                }
                MxcUploadResult(contentUri)
            }
        }
    }

    override suspend fun sendAudioClip(
        config: MatrixConfig,
        roomId: String,
        file: File,
        caption: String,
        fileName: String
    ): MxcUploadResult {
        val mimeType = guessMimeType(file)
        val upload = uploadMedia(config, file, mimeType, fileName)

        val content = JSONObject()
            .put("msgtype", "m.audio")
            .put("body", caption)
            .put("url", upload.contentUri)
            .put(
                "info",
                JSONObject()
                    .put("mimetype", mimeType)
                    .put("size", file.length())
            )

        sendEvent(config, roomId, "m.room.message", content)
        return upload
    }

    override suspend fun sendAudioByMxcUrl(
        config: MatrixConfig,
        roomId: String,
        mxcUrl: String,
        caption: String,
        mimeType: String?,
        sizeBytes: Long?
    ) {
        val info = JSONObject()
        if (!mimeType.isNullOrBlank()) {
            info.put("mimetype", mimeType)
        }
        if (sizeBytes != null && sizeBytes > 0) {
            info.put("size", sizeBytes)
        }
        val content = JSONObject()
            .put("msgtype", "m.audio")
            .put("body", caption)
            .put("url", mxcUrl)
            .put("info", info)

        sendEvent(config, roomId, "m.room.message", content)
    }

    override suspend fun sendFile(
        config: MatrixConfig,
        roomId: String,
        file: File,
        body: String,
        mimeType: String,
        fileName: String
    ): MxcUploadResult {
        val upload = uploadMedia(config, file, mimeType, fileName)

        val content = JSONObject()
            .put("msgtype", "m.file")
            .put("body", body)
            .put("filename", fileName)
            .put("url", upload.contentUri)
            .put(
                "info",
                JSONObject()
                    .put("mimetype", mimeType)
                    .put("size", file.length())
            )

        sendEvent(config, roomId, "m.room.message", content)
        return upload
    }

    override suspend fun sendImage(
        config: MatrixConfig,
        roomId: String,
        file: File,
        body: String,
        mimeType: String,
        width: Int,
        height: Int,
        fileName: String
    ): MxcUploadResult {
        val upload = uploadMedia(config, file, mimeType, fileName)

        val content = JSONObject()
            .put("msgtype", "m.image")
            .put("body", body)
            .put("filename", fileName)
            .put("url", upload.contentUri)
            .put(
                "info",
                JSONObject()
                    .put("mimetype", mimeType)
                    .put("size", file.length())
                    .put("w", width)
                    .put("h", height)
            )

        sendEvent(config, roomId, "m.room.message", content)
        return upload
    }

    override suspend fun sendTestMessage(config: MatrixConfig) {
        val content = JSONObject()
            .put("msgtype", "m.text")
            .put("body", "SL400 test message")
        sendEvent(config, config.roomId, "m.room.message", content)
    }

    override suspend fun sendText(config: MatrixConfig, roomId: String, body: String) {
        val content = JSONObject()
            .put("msgtype", "m.text")
            .put("body", body)
        sendEvent(config, roomId, "m.room.message", content)
    }

    private fun metricValue(metrics: AcousticMetrics, mode: MetricMode): Double? {
        return when (mode) {
            MetricMode.LIVE -> metrics.currentDb
            MetricMode.LAEQ_1_MIN -> metrics.laEq1Min
            MetricMode.LAEQ_5_MIN -> metrics.laEq5Min
            MetricMode.LAEQ_15_MIN -> metrics.laEq15Min
            MetricMode.MAX_1_MIN -> metrics.maxDb1Min
        }
    }

    private fun metricLabel(mode: MetricMode): String {
        return when (mode) {
            MetricMode.LIVE -> "Live"
            MetricMode.LAEQ_1_MIN -> "LAeq 1 min"
            MetricMode.LAEQ_5_MIN -> "LAeq 5 min"
            MetricMode.LAEQ_15_MIN -> "LAeq 15 min"
            MetricMode.MAX_1_MIN -> "Max 1 min"
        }
    }

    private fun guessMimeType(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private suspend fun sendEvent(
        config: MatrixConfig,
        roomId: String,
        eventType: String,
        content: JSONObject
    ) {
        if (config.accessToken.isBlank()) {
            throw IllegalArgumentException("Access token is missing")
        }
        if (roomId.isBlank()) {
            throw IllegalArgumentException("Room ID is missing")
        }

        val base = config.homeserverBaseUrl.trim().removeSuffix("/")
        val baseUrl = base.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Homeserver URL is invalid")

        val txnId = "${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"

        val url = baseUrl.newBuilder()
            .addPathSegments("_matrix/client/v3/rooms")
            .addPathSegment(roomId.trim())
            .addPathSegments("send")
            .addPathSegment(eventType)
            .addPathSegment(txnId)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.accessToken}")
            .put(content.toString().toRequestBody(jsonType))
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.take(500) ?: resp.message
                    throw IllegalStateException("Matrix error ${resp.code}: $msg")
                }
            }
        }
    }
}
