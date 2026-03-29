package de.drremote.trotecsl400.alert

class MatrixQueryCommandHandler : MatrixCommandHandler {
    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        return when (parts[1].lowercase()) {
            "incidents" -> handleIncidents(parts, config)
            "summary" -> handleSummary(parts, config)
            else -> null
        }
    }

    private fun handleIncidents(parts: List<String>, config: AlertConfig): CommandResult {
        val mode = parts.getOrNull(2)?.lowercase()
        if (mode == "since") {
            val value = parts.getOrNull(3)?.lowercase()
            if (value == null) {
                return CommandResult(config, "SL400: Usage `!sl400 incidents since 30m`.")
            }
            val durationMs = MatrixCommandParsing.parseDurationMs(value)
            return if (durationMs == null) {
                CommandResult(config, "SL400: Invalid duration `$value`.")
            } else {
                CommandResult(
                    config,
                    "SL400: fetching incidents since $value",
                    CommandAction.IncidentsSince(durationMs, value)
                )
            }
        }
        if (mode == "between") {
            val startText = parts.getOrNull(3)
            val endText = parts.getOrNull(4)
            if (startText.isNullOrBlank() || endText.isNullOrBlank()) {
                return CommandResult(
                    config,
                    "SL400: Usage `!sl400 incidents between 2026-03-27T18:00 2026-03-27T23:00`."
                )
            }
            val startMs = MatrixCommandParsing.parseDateTimeMs(startText)
            val endMs = MatrixCommandParsing.parseDateTimeMs(endText)
            return if (startMs == null || endMs == null) {
                CommandResult(config, "SL400: Invalid datetime format.")
            } else if (endMs < startMs) {
                CommandResult(config, "SL400: end must be after start.")
            } else {
                val label = "$startText to $endText"
                CommandResult(
                    config,
                    "SL400: fetching incidents between $label",
                    CommandAction.IncidentsBetween(startMs, endMs, label)
                )
            }
        }
        if (mode == "today") {
            return CommandResult(
                config,
                "SL400: fetching incidents today",
                CommandAction.IncidentsToday
            )
        }
        if (mode == "yesterday") {
            return CommandResult(
                config,
                "SL400: fetching incidents yesterday",
                CommandAction.IncidentsYesterday
            )
        }
        return CommandResult(config, "SL400: Usage `!sl400 incidents since 30m`.")
    }

    private fun handleSummary(parts: List<String>, config: AlertConfig): CommandResult {
        val mode = parts.getOrNull(2)?.lowercase()
        if (mode == "since") {
            val value = parts.getOrNull(3)?.lowercase()
            if (value == null) {
                return CommandResult(config, "SL400: Usage `!sl400 summary since 1h`.")
            }
            val durationMs = MatrixCommandParsing.parseDurationMs(value)
            return if (durationMs == null) {
                CommandResult(config, "SL400: Invalid duration `$value`.")
            } else {
                CommandResult(
                    config,
                    "SL400: summary since $value",
                    CommandAction.SummarySince(durationMs, value)
                )
            }
        }
        if (mode == "today") {
            return CommandResult(
                config,
                "SL400: summary today",
                CommandAction.SummaryToday
            )
        }
        if (mode == "yesterday") {
            return CommandResult(
                config,
                "SL400: summary yesterday",
                CommandAction.SummaryYesterday
            )
        }
        return CommandResult(config, "SL400: Usage `!sl400 summary since 1h`.")
    }
}
