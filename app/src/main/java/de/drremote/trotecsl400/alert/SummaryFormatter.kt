package de.drremote.trotecsl400.alert

import de.drremote.trotecsl400.incident.IncidentRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SummaryFormatter {
    fun buildSummaryMessage(
        incidents: List<IncidentRecord>,
        label: String,
        audioRunning: Boolean?
    ): String {
        if (incidents.isEmpty()) {
            return "SL400 summary ($label): no incidents."
        }

        val maxMetricIncident =
            incidents.maxByOrNull { it.metricValue ?: Double.MIN_VALUE }
        val maxLaeq5 = incidents.mapNotNull { it.laEq5Min }.maxOrNull()
        val timeAboveMs = incidents.sumOf { it.timeAboveThresholdMs1Min }
        val clipsSaved = incidents.count { it.clipPath.isNotBlank() }
        val avgMetric = incidents.mapNotNull { it.metricValue }.average().takeIf { !it.isNaN() }
        val earliest = incidents.minByOrNull { it.timestampMs }?.timestampMs
        val latest = incidents.maxByOrNull { it.timestampMs }?.timestampMs
        val topHints = incidents.mapNotNull { it.audioHint?.takeIf { hint -> hint.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key} (${it.value})" }

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val topIncidents = incidents
            .sortedByDescending { it.metricValue ?: Double.MIN_VALUE }
            .take(3)

        return buildString {
            append("SL400 Summary - $label\n\n")
            append("Incidents: ${incidents.size}\n")
            append("Highest incident: ${IncidentFormatting.formatDb(maxMetricIncident?.metricValue)}\n")
            append("Highest LAeq 5 min: ${IncidentFormatting.formatDb(maxLaeq5)}\n")
            if (avgMetric != null) {
                append("Average incident: ${IncidentFormatting.formatDb(avgMetric)}\n")
            }
            append(
                "Summed incident 1-min above-threshold windows: " +
                    "${IncidentFormatting.formatDuration(timeAboveMs)}\n"
            )
            append("Saved clips: $clipsSaved\n")
            when (audioRunning) {
                true -> append("Audio buffer: running\n")
                false -> append("Audio buffer: stopped\n")
                null -> append("Audio buffer: unknown\n")
            }
            if (earliest != null && latest != null) {
                append(
                    "First/last incident: " +
                        "${formatter.format(Date(earliest))} / ${formatter.format(Date(latest))}\n"
                )
            }
            if (topHints.isNotBlank()) {
                append("Common audio hints: $topHints\n")
            }
            append("\nTop incidents:\n")
            topIncidents.forEachIndexed { index, incident ->
                val time = formatter.format(Date(incident.timestampMs))
                val metricLabel = IncidentFormatting.formatMetricMode(incident.metricMode)
                val metricValue = IncidentFormatting.formatDb(incident.metricValue)
                val clip = if (incident.clipPath.isNotBlank()) "yes" else "no"
                val hint = incident.audioHint?.takeIf { it.isNotBlank() }
                append("${index + 1}) $time | id=${incident.incidentId} | $metricLabel $metricValue")
                if (hint != null) append(" | $hint")
                append(" | clip=$clip\n")
            }
        }
    }
}
