package de.drremote.trotecsl400.incident

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class IncidentRepository(context: Context) {
    private val dir = File(context.filesDir, "incidents")
    private val file = File(dir, "incidents.jsonl")
    private val mutex = Mutex()

    suspend fun add(record: IncidentRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureDir()
            file.appendText(record.toJson().toString() + "\n")
        }
    }

    suspend fun cleanup(
        retentionMs: Long,
        maxRecords: Int,
        maxClips: Int,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = readAll().sortedBy { it.timestampMs }.toMutableList()
            val beforeClipPaths = records
                .mapNotNull { it.clipPath.takeIf { p -> p.isNotBlank() } }
                .toSet()
            val cutoff = nowMs - retentionMs
            val kept = records.filter { it.timestampMs >= cutoff }.toMutableList()

            // Enforce max records (keep newest)
            val trimmedByCount = if (kept.size > maxRecords) {
                kept.takeLast(maxRecords).toMutableList()
            } else {
                kept
            }

            // Enforce max clips by removing oldest clip refs
            val clips = trimmedByCount.filter { it.clipPath.isNotBlank() }
                .sortedBy { it.timestampMs }
            val clipsToRemove = if (clips.size > maxClips) {
                clips.take(clips.size - maxClips)
            } else {
                emptyList()
            }
            val clipPathsToRemove = clipsToRemove.map { it.clipPath }.toSet()
            val final = trimmedByCount.map {
                if (it.clipPath in clipPathsToRemove) {
                    it.copy(clipPath = "", clipUploaded = false, mxcUrl = null)
                } else {
                    it
                }
            }

            writeAll(final)

            val afterClipPaths = final
                .mapNotNull { it.clipPath.takeIf { p -> p.isNotBlank() } }
                .toSet()
            val filesToDelete = beforeClipPaths - afterClipPaths
            filesToDelete.forEach { path ->
                runCatching { File(path).delete() }
            }
        }
    }

    suspend fun updateClip(
        incidentId: String,
        clipPath: String,
        clipUploaded: Boolean = false,
        mxcUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = readAll().map {
                if (it.incidentId == incidentId) {
                    it.copy(
                        clipPath = clipPath,
                        clipUploaded = clipUploaded,
                        mxcUrl = mxcUrl ?: it.mxcUrl
                    )
                } else {
                    it
                }
            }
            writeAll(all)
        }
    }

    suspend fun updateAudioHint(
        incidentId: String,
        audioHint: String?
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = readAll().map {
                if (it.incidentId == incidentId) {
                    it.copy(audioHint = audioHint)
                } else {
                    it
                }
            }
            writeAll(all)
        }
    }

    suspend fun markUploaded(incidentId: String, mxcUrl: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = readAll().map {
                if (it.incidentId == incidentId) {
                    it.copy(clipUploaded = true, mxcUrl = mxcUrl)
                } else {
                    it
                }
            }
            writeAll(all)
        }
    }

    suspend fun getIncidentsSince(
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<IncidentRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cutoff = nowMs - durationMs
            readAll().filter { it.timestampMs >= cutoff }
        }
    }

    suspend fun getIncidentsBetween(
        startMs: Long,
        endMs: Long
    ): List<IncidentRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readAll().filter { it.timestampMs >= startMs && it.timestampMs < endMs }
        }
    }

    suspend fun getLastClipIncident(): IncidentRecord? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readAll()
                .filter { it.clipPath.isNotBlank() }
                .maxByOrNull { it.timestampMs }
        }
    }

    suspend fun getIncidentById(incidentId: String): IncidentRecord? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readAll().firstOrNull { it.incidentId == incidentId }
        }
    }

    suspend fun getClipsSince(
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<IncidentRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cutoff = nowMs - durationMs
            readAll().filter { it.timestampMs >= cutoff && it.clipPath.isNotBlank() }
        }
    }

    private fun ensureDir() {
        if (!dir.exists()) dir.mkdirs()
    }

    private fun readAll(): List<IncidentRecord> {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) null else IncidentRecord.fromJson(JSONObject(trimmed))
            }
    }

    private fun writeAll(records: List<IncidentRecord>) {
        ensureDir()
        file.writeText("")
        records.forEach { file.appendText(it.toJson().toString() + "\n") }
    }

    private fun IncidentRecord.toJson(): JSONObject {
        return JSONObject()
            .put("incidentId", incidentId)
            .put("timestampMs", timestampMs)
            .put("roomId", roomId)
            .put("metricMode", metricMode)
            .put("metricValue", metricValue)
            .put("thresholdDb", thresholdDb)
            .put("laEq1Min", laEq1Min)
            .put("laEq5Min", laEq5Min)
            .put("laEq15Min", laEq15Min)
            .put("maxDb1Min", maxDb1Min)
            .put("timeAboveThresholdMs1Min", timeAboveThresholdMs1Min)
            .put("clipPath", clipPath)
            .put("clipUploaded", clipUploaded)
            .put("mxcUrl", mxcUrl)
            .put("audioHint", audioHint)
    }

    private companion object {
        fun IncidentRecord.Companion.fromJson(json: JSONObject): IncidentRecord {
            return IncidentRecord(
                incidentId = json.optString("incidentId"),
                timestampMs = json.optLong("timestampMs"),
                roomId = json.optString("roomId"),
                metricMode = json.optString("metricMode"),
                metricValue = json.optDouble("metricValue").takeIf { !it.isNaN() },
                thresholdDb = json.optDouble("thresholdDb"),
                laEq1Min = json.optDouble("laEq1Min").takeIf { !it.isNaN() },
                laEq5Min = json.optDouble("laEq5Min").takeIf { !it.isNaN() },
                laEq15Min = json.optDouble("laEq15Min").takeIf { !it.isNaN() },
                maxDb1Min = json.optDouble("maxDb1Min").takeIf { !it.isNaN() },
                timeAboveThresholdMs1Min = json.optLong("timeAboveThresholdMs1Min"),
                clipPath = json.optString("clipPath"),
                clipUploaded = json.optBoolean("clipUploaded"),
                mxcUrl = json.optString("mxcUrl").ifBlank { null },
                audioHint = json.optString("audioHint").ifBlank { null }
            )
        }
    }
}
