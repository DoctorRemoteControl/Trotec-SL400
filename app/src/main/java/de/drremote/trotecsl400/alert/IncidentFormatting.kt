package de.drremote.trotecsl400.alert

import java.util.Locale

object IncidentFormatting {
    fun formatDb(value: Double?): String {
        return value?.let { String.format(Locale.US, "%.1f dB", it) } ?: "n/a"
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatMetricMode(mode: String): String {
        return when (mode) {
            MetricMode.LIVE.name -> "Live"
            MetricMode.LAEQ_1_MIN.name -> "LAeq 1 min"
            MetricMode.LAEQ_5_MIN.name -> "LAeq 5 min"
            MetricMode.LAEQ_15_MIN.name -> "LAeq 15 min"
            MetricMode.MAX_1_MIN.name -> "Max 1 min"
            else -> mode
        }
    }
}
