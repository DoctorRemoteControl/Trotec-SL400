package de.drremote.trotecsl400.alert

class MatrixGraphCommandHandler : MatrixCommandHandler {
    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        return when (parts[1].lowercase()) {
            "graph" -> handleGraph(parts, config)
            else -> null
        }
    }

    private fun handleGraph(parts: List<String>, config: AlertConfig): CommandResult {
        val mode = parts.getOrNull(2)?.lowercase()
        if (mode == "since") {
            val value = parts.getOrNull(3)?.lowercase()
            if (value == null) {
                return CommandResult(config, "SL400: Usage `!sl400 graph since 6h`.")
            }
            val durationMs = MatrixCommandParsing.parseDurationMs(value)
            return if (durationMs == null) {
                CommandResult(config, "SL400: Invalid duration `$value`.")
            } else {
                CommandResult(
                    config,
                    "SL400: graph since $value",
                    CommandAction.GraphSince(durationMs, value)
                )
            }
        }
        if (mode == "today") {
            return CommandResult(
                config,
                "SL400: graph today",
                CommandAction.GraphToday
            )
        }
        if (mode == "yesterday") {
            return CommandResult(
                config,
                "SL400: graph yesterday",
                CommandAction.GraphYesterday
            )
        }
        return CommandResult(config, "SL400: Usage `!sl400 graph since 6h`.")
    }
}
