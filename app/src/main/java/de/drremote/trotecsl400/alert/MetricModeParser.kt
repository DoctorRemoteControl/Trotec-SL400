package de.drremote.trotecsl400.alert

object MetricModeParser {
    fun parse(value: String?): MetricMode? {
        val v = value?.lowercase() ?: return null
        return when (v) {
            "live" -> MetricMode.LIVE
            "laeq1", "laeq1min", "laeq_1_min" -> MetricMode.LAEQ_1_MIN
            "laeq5", "laeq5min", "laeq_5_min" -> MetricMode.LAEQ_5_MIN
            "laeq15", "laeq15min", "laeq_15_min" -> MetricMode.LAEQ_15_MIN
            "max1", "max1min", "max_1_min" -> MetricMode.MAX_1_MIN
            else -> null
        }
    }
}
