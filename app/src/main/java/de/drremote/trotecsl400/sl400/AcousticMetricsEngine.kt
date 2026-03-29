package de.drremote.trotecsl400.sl400

import kotlin.math.log10
import kotlin.math.pow

class AcousticMetricsEngine {
    private val samples = ArrayDeque<Sl400Sample>()

    fun reset() {
        samples.clear()
    }

    fun addSample(sample: Sl400Sample, thresholdDb: Double): AcousticMetrics {
        samples.addLast(sample)

        val now = sample.timestampMs
        trimOld(now)

        val stats1 = windowStats(now, ONE_MINUTE_MS, thresholdDb)
        val stats5 = windowStats(now, FIVE_MINUTES_MS, null)
        val stats15 = windowStats(now, FIFTEEN_MINUTES_MS, null)

        return AcousticMetrics(
            timestampMs = now,
            currentDb = sample.db,
            laEq1Min = laEqFromStats(stats1),
            laEq5Min = laEqFromStats(stats5),
            laEq15Min = laEqFromStats(stats15),
            maxDb1Min = maxForWindow(now, ONE_MINUTE_MS),
            timeAboveThresholdMs1Min = stats1.timeAboveThresholdMs,
            coverage1MinMs = stats1.totalDurationMs,
            coverage5MinMs = stats5.totalDurationMs,
            coverage15MinMs = stats15.totalDurationMs
        )
    }

    private fun trimOld(now: Long) {
        while (samples.isNotEmpty() && now - samples.first().timestampMs > FIFTEEN_MINUTES_MS) {
            samples.removeFirst()
        }
    }

    private fun windowSamples(now: Long, windowMs: Long): List<Sl400Sample> {
        return samples.filter { it.timestampMs in (now - windowMs)..now }
    }

    private fun laEqFromStats(stats: WindowStats): Double? {
        if (stats.totalDurationMs <= 0L) return null
        val meanEnergy = stats.energyTimeSum / stats.totalDurationMs.toDouble()
        return 10.0 * log10(meanEnergy)
    }

    private fun maxForWindow(now: Long, windowMs: Long): Double? {
        return windowSamples(now, windowMs).maxOfOrNull { it.db }
    }

    private fun windowStats(
        now: Long,
        windowMs: Long,
        thresholdDb: Double?
    ): WindowStats {
        val windowStart = now - windowMs
        val windowSamples = windowSamples(now, windowMs)
        if (windowSamples.isEmpty()) return WindowStats(0L, 0.0, 0L)

        var totalDurationMs = 0L
        var energyTimeSum = 0.0
        var timeAboveThresholdMs = 0L

        for (i in windowSamples.indices) {
            val current = windowSamples[i]
            val start = maxOf(current.timestampMs, windowStart)
            val end = if (i + 1 < windowSamples.size) {
                minOf(windowSamples[i + 1].timestampMs, now)
            } else {
                now
            }
            val rawDt = end - start
            val dt = rawDt.coerceIn(0L, MAX_VALID_SEGMENT_MS)
            if (dt > 0L) {
                totalDurationMs += dt
                val energy = 10.0.pow(current.db / 10.0)
                energyTimeSum += energy * dt.toDouble()
                if (thresholdDb != null && current.db >= thresholdDb) {
                    timeAboveThresholdMs += dt
                }
            }
        }

        return WindowStats(totalDurationMs, energyTimeSum, timeAboveThresholdMs)
    }

    private data class WindowStats(
        val totalDurationMs: Long,
        val energyTimeSum: Double,
        val timeAboveThresholdMs: Long
    )

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
        const val FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS
        const val FIFTEEN_MINUTES_MS = 15 * ONE_MINUTE_MS
        const val MAX_VALID_SEGMENT_MS = 2_000L
    }
}
