package de.drremote.trotecsl400.alert

data class AlertConfig(
    val enabled: Boolean = false,
    val thresholdDb: Double = 70.0,
    val hysteresisDb: Double = 2.0,
    val minSendIntervalMs: Long = 60_000,
    val sendMode: SendMode = SendMode.CROSSING_ONLY,
    val metricMode: MetricMode = MetricMode.LAEQ_5_MIN,
    val allowedSenders: List<String> = emptyList(),
    val commandRoomId: String = "",
    val targetRoomId: String = "",
    val alertHintFollowupEnabled: Boolean = true,
    val dailyReportEnabled: Boolean = false,
    val dailyReportHour: Int = 9,
    val dailyReportMinute: Int = 0,
    val dailyReportRoomId: String = "",
    val dailyReportJsonEnabled: Boolean = true,
    val dailyReportGraphEnabled: Boolean = true
)

enum class SendMode {
    CROSSING_ONLY,
    PERIODIC_WHILE_ABOVE
}

enum class MetricMode {
    LIVE,
    LAEQ_1_MIN,
    LAEQ_5_MIN,
    LAEQ_15_MIN,
    MAX_1_MIN
}
