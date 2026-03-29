package de.drremote.trotecsl400.alert

import android.content.Context
import de.drremote.trotecsl400.incident.IncidentRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JsonExporter {
    fun writeIncidentsJson(
        context: Context,
        incidents: List<IncidentRecord>,
        label: String
    ): File {
        val safeLabel = label
            .replace(Regex("[^a-zA-Z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "all" }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "sl400_incidents_${safeLabel}_$timestamp.json"
        val file = File(context.cacheDir, fileName)

        val sorted = incidents.sortedBy { it.timestampMs }

        val root = JSONObject()
            .put("schema", "de.drremote.trotecsl400.incidents-export.v1")
            .put("label", label)
            .put("generatedAtMs", System.currentTimeMillis())
            .put("incidentCount", sorted.size)

        val items = JSONArray()
        sorted.forEach { incident ->
            items.put(
                JSONObject()
                    .put("incidentId", incident.incidentId)
                    .put("timestampMs", incident.timestampMs)
                    .put("roomId", incident.roomId)
                    .put("metricMode", incident.metricMode)
                    .put("metricValue", incident.metricValue ?: JSONObject.NULL)
                    .put("thresholdDb", incident.thresholdDb)
                    .put("laEq1Min", incident.laEq1Min ?: JSONObject.NULL)
                    .put("laEq5Min", incident.laEq5Min ?: JSONObject.NULL)
                    .put("laEq15Min", incident.laEq15Min ?: JSONObject.NULL)
                    .put("maxDb1Min", incident.maxDb1Min ?: JSONObject.NULL)
                    .put("timeAboveThresholdMs1Min", incident.timeAboveThresholdMs1Min)
                    .put("audioHint", incident.audioHint ?: JSONObject.NULL)
                    .put("clipPath", incident.clipPath.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    .put("clipUploaded", incident.clipUploaded)
                    .put("mxcUrl", incident.mxcUrl ?: JSONObject.NULL)
            )
        }

        root.put("incidents", items)
        file.writeText(root.toString(2))
        return file
    }
}
