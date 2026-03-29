package de.drremote.trotecsl400.alert

class MatrixConfigCommandHandler : MatrixCommandHandler {
    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        val command = parts[1].lowercase()
        if (command == "status" || command == "config") {
            return CommandResult(config, statusMessage(config))
        }
        if (command == "set") {
            val key = parts.getOrNull(2)?.lowercase()
            val value = parts.getOrNull(3)
            if (key.isNullOrBlank()) {
                return CommandResult(config, "SL400: Usage `!sl400 set <key> <value>`.")
            }
            return handleSetting(key, value, config, fromSet = true)
        }
        return handleSetting(command, parts.getOrNull(2), config, fromSet = false)
    }

    private fun statusMessage(cfg: AlertConfig): String {
        return "SL400: enabled=${cfg.enabled}, thresholdDb=${cfg.thresholdDb}, " +
            "hysteresisDb=${cfg.hysteresisDb}, minSendIntervalMs=${cfg.minSendIntervalMs}, " +
            "sendMode=${cfg.sendMode}, metricMode=${cfg.metricMode}, " +
            "commandRoomId=${cfg.commandRoomId}, targetRoomId=${cfg.targetRoomId}, " +
            "allowedSenders=${cfg.allowedSenders.joinToString()}, " +
            "alertHintFollowupEnabled=${cfg.alertHintFollowupEnabled}, " +
            "dailyReportEnabled=${cfg.dailyReportEnabled}, dailyReportTime=${formatTime(cfg)}, " +
            "dailyReportRoomId=${cfg.dailyReportRoomId}, dailyReportJsonEnabled=${cfg.dailyReportJsonEnabled}, " +
            "dailyReportGraphEnabled=${cfg.dailyReportGraphEnabled}"
    }

    private fun handleSetting(
        command: String,
        valueRaw: String?,
        config: AlertConfig,
        fromSet: Boolean
    ): CommandResult? {
        return when (command) {
            "enable" -> {
                val enabled = valueRaw?.toBooleanStrictOrNull()
                if (enabled == null) {
                    usageMessage(config, fromSet, "enable", "true|false")
                } else {
                    val updated = config.copy(enabled = enabled)
                    CommandResult(updated, "SL400: enabled=$enabled")
                }
            }
            "threshold" -> {
                val value = valueRaw?.toDoubleOrNull()
                if (value == null) {
                    usageMessage(config, fromSet, "threshold", "<number>")
                } else {
                    val updated = config.copy(thresholdDb = value)
                    CommandResult(updated, "SL400: thresholdDb=$value")
                }
            }
            "hysteresis" -> {
                val value = valueRaw?.toDoubleOrNull()
                if (value == null) {
                    usageMessage(config, fromSet, "hysteresis", "<number>")
                } else {
                    val updated = config.copy(hysteresisDb = value)
                    CommandResult(updated, "SL400: hysteresisDb=$value")
                }
            }
            "interval" -> {
                val value = valueRaw?.toLongOrNull()
                if (value == null || value < 0) {
                    usageMessage(config, fromSet, "interval", "<millis>")
                } else {
                    val updated = config.copy(minSendIntervalMs = value)
                    CommandResult(updated, "SL400: minSendIntervalMs=$value")
                }
            }
            "dailyreport" -> {
                val enabled = valueRaw?.toBooleanStrictOrNull()
                if (enabled == null) {
                    usageMessage(config, fromSet, "dailyreport", "true|false")
                } else {
                    val updated = config.copy(dailyReportEnabled = enabled)
                    CommandResult(updated, "SL400: dailyReportEnabled=$enabled")
                }
            }
            "reporttime" -> {
                val time = parseHourMinute(valueRaw)
                if (time == null) {
                    usageMessage(config, fromSet, "reporttime", "HH:MM")
                } else {
                    val updated = config.copy(
                        dailyReportHour = time.first,
                        dailyReportMinute = time.second
                    )
                    CommandResult(
                        updated,
                        "SL400: dailyReportTime=${formatTime(updated)}"
                    )
                }
            }
            "reportroom" -> {
                val value = valueRaw
                if (value.isNullOrBlank()) {
                    usageMessage(config, fromSet, "reportroom", "<roomId>")
                } else {
                    val updated = config.copy(dailyReportRoomId = value)
                    CommandResult(updated, "SL400: dailyReportRoomId=$value")
                }
            }
            "reportjson" -> {
                val enabled = valueRaw?.toBooleanStrictOrNull()
                if (enabled == null) {
                    usageMessage(config, fromSet, "reportjson", "true|false")
                } else {
                    val updated = config.copy(dailyReportJsonEnabled = enabled)
                    CommandResult(updated, "SL400: dailyReportJsonEnabled=$enabled")
                }
            }
            "reportgraph" -> {
                val enabled = valueRaw?.toBooleanStrictOrNull()
                if (enabled == null) {
                    usageMessage(config, fromSet, "reportgraph", "true|false")
                } else {
                    val updated = config.copy(dailyReportGraphEnabled = enabled)
                    CommandResult(updated, "SL400: dailyReportGraphEnabled=$enabled")
                }
            }
            "alerthint" -> {
                val enabled = valueRaw?.toBooleanStrictOrNull()
                if (enabled == null) {
                    usageMessage(config, fromSet, "alerthint", "true|false")
                } else {
                    val updated = config.copy(alertHintFollowupEnabled = enabled)
                    CommandResult(updated, "SL400: alertHintFollowupEnabled=$enabled")
                }
            }
            "mode" -> {
                val value = valueRaw?.lowercase()
                val mode = when (value) {
                    "crossing" -> SendMode.CROSSING_ONLY
                    "periodic" -> SendMode.PERIODIC_WHILE_ABOVE
                    else -> null
                }
                if (mode == null) {
                    usageMessage(config, fromSet, "mode", "crossing|periodic")
                } else {
                    val updated = config.copy(sendMode = mode)
                    CommandResult(updated, "SL400: sendMode=$mode")
                }
            }
            "metric" -> {
                val metric = MetricModeParser.parse(valueRaw)
                if (metric == null) {
                    usageMessage(config, fromSet, "metric", "live|laeq1|laeq5|laeq15|max1")
                } else {
                    val updated = config.copy(metricMode = metric)
                    CommandResult(updated, "SL400: metricMode=$metric")
                }
            }
            "commandroom" -> {
                val value = valueRaw
                if (value.isNullOrBlank()) {
                    usageMessage(config, fromSet, "commandroom", "<roomId>")
                } else {
                    val updated = config.copy(commandRoomId = value)
                    CommandResult(updated, "SL400: commandRoomId=$value")
                }
            }
            "targetroom" -> {
                val value = valueRaw
                if (value.isNullOrBlank()) {
                    usageMessage(config, fromSet, "targetroom", "<roomId>")
                } else {
                    val updated = config.copy(targetRoomId = value)
                    CommandResult(updated, "SL400: targetRoomId=$value")
                }
            }
            "allow" -> {
                val value = valueRaw
                if (value.isNullOrBlank()) {
                    usageMessage(config, fromSet, "allow", "<@user:server>")
                } else {
                    val updated = config.copy(
                        allowedSenders = (config.allowedSenders + value).distinct()
                    )
                    CommandResult(
                        updated,
                        "SL400: allowedSenders=${updated.allowedSenders.joinToString()}"
                    )
                }
            }
            "deny" -> {
                val value = valueRaw
                if (value.isNullOrBlank()) {
                    usageMessage(config, fromSet, "deny", "<@user:server>")
                } else {
                    val updated = config.copy(
                        allowedSenders = config.allowedSenders.filterNot { it == value }
                    )
                    CommandResult(
                        updated,
                        "SL400: allowedSenders=${updated.allowedSenders.joinToString()}"
                    )
                }
            }
            else -> null
        }
    }

    private fun usageMessage(
        config: AlertConfig,
        fromSet: Boolean,
        key: String,
        valueHint: String
    ): CommandResult {
        return if (fromSet) {
            CommandResult(config, "SL400: Usage `!sl400 set $key $valueHint`.")
        } else {
            CommandResult(config, "SL400: Usage `!sl400 $key $valueHint`.")
        }
    }

    private fun parseHourMinute(value: String?): Pair<Int, Int>? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun formatTime(cfg: AlertConfig): String {
        return String.format("%02d:%02d", cfg.dailyReportHour, cfg.dailyReportMinute)
    }
}
