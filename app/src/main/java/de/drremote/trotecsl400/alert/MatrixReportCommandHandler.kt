package de.drremote.trotecsl400.alert

class MatrixReportCommandHandler : MatrixCommandHandler {
    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        return when (parts[1].lowercase()) {
            "report" -> handleReport(parts, config)
            else -> null
        }
    }

    private fun handleReport(parts: List<String>, config: AlertConfig): CommandResult {
        val mode = parts.getOrNull(2)?.lowercase()
        if (mode == null || mode == "now") {
            return CommandResult(
                config,
                "SL400: report now",
                CommandAction.ReportNow
            )
        }
        if (mode == "today") {
            return CommandResult(
                config,
                "SL400: report today",
                CommandAction.ReportToday
            )
        }
        if (mode == "yesterday") {
            return CommandResult(
                config,
                "SL400: report yesterday",
                CommandAction.ReportYesterday
            )
        }
        if (mode == "since") {
            val value = parts.getOrNull(3)?.lowercase()
            if (value == null) {
                return CommandResult(config, "SL400: Usage `!sl400 report since 24h`.")
            }
            val durationMs = MatrixCommandParsing.parseDurationMs(value)
            return if (durationMs == null) {
                CommandResult(config, "SL400: Invalid duration `$value`.")
            } else {
                CommandResult(
                    config,
                    "SL400: report since $value",
                    CommandAction.ReportSince(durationMs, value)
                )
            }
        }
        return CommandResult(config, "SL400: Usage `!sl400 report now|today|yesterday|since <duration>`.")
    }
}
