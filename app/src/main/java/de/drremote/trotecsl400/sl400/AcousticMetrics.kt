package de.drremote.trotecsl400.sl400

data class AcousticMetrics(
    val timestampMs: Long,
    val currentDb: Double,
    val laEq1Min: Double?,
    val laEq5Min: Double?,
    val laEq15Min: Double?,
    val maxDb1Min: Double?,
    val timeAboveThresholdMs1Min: Long,
    val coverage1MinMs: Long,
    val coverage5MinMs: Long,
    val coverage15MinMs: Long
)
