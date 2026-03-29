package de.drremote.trotecsl400.alert

class MatrixExportCommandHandler : MatrixCommandHandler {
    private val metricParser = MetricModeParser

    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        return when (parts[1].lowercase()) {
            "json" -> handleJson(parts, config)
            else -> null
        }
    }

    private fun handleJson(parts: List<String>, config: AlertConfig): CommandResult {
        val mode = parts.getOrNull(2)?.lowercase()

        if (mode == "since") {
            val value = parts.getOrNull(3)?.lowercase()
            val options = parseOptions(parts.drop(4))
            if (options.error != null) {
                return CommandResult(config, "SL400: ${options.error}")
            }
            if (value == null) {
                return CommandResult(config, "SL400: Usage `!sl400 json since 24h`.")
            }
            val durationMs = MatrixCommandParsing.parseDurationMs(value)
            return if (durationMs == null) {
                CommandResult(config, "SL400: Invalid duration `$value`.")
            } else {
                CommandResult(
                    config,
                    "SL400: exporting json since $value",
                    CommandAction.JsonSince(
                        durationMs = durationMs,
                        label = value,
                        clipOnly = options.clipOnly,
                        metricMode = options.metricMode
                    )
                )
            }
        }

        val options = parseOptions(parts.drop(3))
        if (options.error != null) {
            return CommandResult(config, "SL400: ${options.error}")
        }

        if (mode == "today") {
            return CommandResult(
                config,
                "SL400: exporting json today",
                CommandAction.JsonToday(
                    clipOnly = options.clipOnly,
                    metricMode = options.metricMode
                )
            )
        }

        if (mode == "yesterday") {
            return CommandResult(
                config,
                "SL400: exporting json yesterday",
                CommandAction.JsonYesterday(
                    clipOnly = options.clipOnly,
                    metricMode = options.metricMode
                )
            )
        }

        return CommandResult(config, "SL400: Usage `!sl400 json since 24h`.")
    }

    private data class JsonOptions(
        val clipOnly: Boolean,
        val metricMode: MetricMode?,
        val error: String?
    )

    private fun parseOptions(tokens: List<String>): JsonOptions {
        var clipOnly = false
        var metricMode: MetricMode? = null

        var idx = 0
        while (idx < tokens.size) {
            val token = tokens[idx].lowercase()
            when {
                token == "cliponly" || token == "clips" || token == "clip" -> {
                    clipOnly = true
                    idx += 1
                }
                token.startsWith("metric=") -> {
                    val value = token.substringAfter("metric=")
                    metricMode = metricParser.parse(value) ?: return JsonOptions(
                        clipOnly,
                        metricMode,
                        "Invalid metric `$value`."
                    )
                    idx += 1
                }
                token == "metric" -> {
                    val value = tokens.getOrNull(idx + 1)
                        ?: return JsonOptions(clipOnly, metricMode, "Missing metric value.")
                    metricMode = metricParser.parse(value) ?: return JsonOptions(
                        clipOnly,
                        metricMode,
                        "Invalid metric `$value`."
                    )
                    idx += 2
                }
                token.isBlank() -> idx += 1
                else -> return JsonOptions(clipOnly, metricMode, "Unknown option `$token`.")
            }
        }

        return JsonOptions(clipOnly, metricMode, null)
    }
}