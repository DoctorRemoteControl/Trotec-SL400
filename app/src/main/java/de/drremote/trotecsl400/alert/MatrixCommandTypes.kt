package de.drremote.trotecsl400.alert

data class CommandResult(
    val updatedConfig: AlertConfig,
    val responseMessage: String,
    val action: CommandAction? = null
)

sealed class CommandAction {
    object AudioStart : CommandAction()
    object AudioStop : CommandAction()
    object AudioStatus : CommandAction()
    data class IncidentsSince(val durationMs: Long, val label: String) : CommandAction()
    data class IncidentsBetween(val startMs: Long, val endMs: Long, val label: String) :
        CommandAction()
    object IncidentsToday : CommandAction()
    object IncidentsYesterday : CommandAction()
    data class ClipsSince(val durationMs: Long, val label: String) : CommandAction()
    object ClipLast : CommandAction()
    data class ClipIncident(val incidentId: String) : CommandAction()
    data class SummarySince(val durationMs: Long, val label: String) : CommandAction()
    object SummaryToday : CommandAction()
    object SummaryYesterday : CommandAction()
    data class JsonSince(
        val durationMs: Long,
        val label: String,
        val clipOnly: Boolean = false,
        val metricMode: MetricMode? = null
    ) : CommandAction()
    data class JsonToday(
        val clipOnly: Boolean = false,
        val metricMode: MetricMode? = null
    ) : CommandAction()
    data class JsonYesterday(
        val clipOnly: Boolean = false,
        val metricMode: MetricMode? = null
    ) : CommandAction()
    object ReportNow : CommandAction()
    object ReportToday : CommandAction()
    object ReportYesterday : CommandAction()
    data class ReportSince(val durationMs: Long, val label: String) : CommandAction()
    data class GraphSince(val durationMs: Long, val label: String) : CommandAction()
    object GraphToday : CommandAction()
    object GraphYesterday : CommandAction()
}
