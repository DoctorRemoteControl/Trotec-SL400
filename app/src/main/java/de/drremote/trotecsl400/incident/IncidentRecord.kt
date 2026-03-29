package de.drremote.trotecsl400.incident

data class IncidentRecord(
    val incidentId: String,
    val timestampMs: Long,
    val roomId: String,
    val metricMode: String,
    val metricValue: Double?,
    val thresholdDb: Double,
    val laEq1Min: Double?,
    val laEq5Min: Double?,
    val laEq15Min: Double?,
    val maxDb1Min: Double?,
    val timeAboveThresholdMs1Min: Long,
    val clipPath: String,
    val clipUploaded: Boolean,
    val mxcUrl: String?,
    val audioHint: String?
) {
    companion object
}
