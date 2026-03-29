package de.drremote.trotecsl400.alert

import de.drremote.trotecsl400.sl400.AcousticMetrics

class AlarmEvaluator(
    private var isAbove: Boolean = false,
    private var lastSentAt: Long = 0L
) {
    fun reset() {
        isAbove = false
        lastSentAt = 0L
    }

    fun shouldSend(metrics: AcousticMetrics, cfg: AlertConfig): Boolean {
        if (!cfg.enabled) return false

        if (!hasRequiredCoverage(metrics, cfg.metricMode)) return false

        val now = metrics.timestampMs
        val value = when (cfg.metricMode) {
            MetricMode.LIVE -> metrics.currentDb
            MetricMode.LAEQ_1_MIN -> metrics.laEq1Min
            MetricMode.LAEQ_5_MIN -> metrics.laEq5Min
            MetricMode.LAEQ_15_MIN -> metrics.laEq15Min
            MetricMode.MAX_1_MIN -> metrics.maxDb1Min
        } ?: return false

        val above = value >= cfg.thresholdDb
        val resetLevel = cfg.thresholdDb - cfg.hysteresisDb

        if (!isAbove && above) {
            isAbove = true
            lastSentAt = now
            return true
        }

        if (isAbove && value <= resetLevel) {
            isAbove = false
        }

        if (cfg.sendMode == SendMode.PERIODIC_WHILE_ABOVE &&
            isAbove &&
            now - lastSentAt >= cfg.minSendIntervalMs
        ) {
            lastSentAt = now
            return true
        }

        return false
    }

    private fun hasRequiredCoverage(metrics: AcousticMetrics, mode: MetricMode): Boolean {
        return when (mode) {
            MetricMode.LAEQ_1_MIN,
            MetricMode.MAX_1_MIN -> metrics.coverage1MinMs >= ONE_MINUTE_MS
            MetricMode.LAEQ_5_MIN -> metrics.coverage5MinMs >= FIVE_MINUTES_MS
            MetricMode.LAEQ_15_MIN -> metrics.coverage15MinMs >= FIFTEEN_MINUTES_MS
            else -> true
        }
    }

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
        const val FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS
        const val FIFTEEN_MINUTES_MS = 15 * ONE_MINUTE_MS
    }
}
