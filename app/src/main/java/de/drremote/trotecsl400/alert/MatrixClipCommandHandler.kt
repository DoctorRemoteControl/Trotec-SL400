package de.drremote.trotecsl400.alert

class MatrixClipCommandHandler : MatrixCommandHandler {
    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        return when (parts[1].lowercase()) {
            "clip" -> handleClip(parts, config)
            "clips" -> handleClips(parts, config)
            else -> null
        }
    }

    private fun handleClip(parts: List<String>, config: AlertConfig): CommandResult {
        val mode = parts.getOrNull(2)?.lowercase()
        return if (mode == "last") {
            CommandResult(config, "SL400: uploading last clip", CommandAction.ClipLast)
        } else if (mode == "incident") {
            val id = parts.getOrNull(3)
            if (id.isNullOrBlank()) {
                CommandResult(config, "SL400: Usage `!sl400 clip incident <incidentId>`.")
            } else {
                CommandResult(
                    config,
                    "SL400: uploading clip for incident $id",
                    CommandAction.ClipIncident(id)
                )
            }
        } else {
            CommandResult(config, "SL400: Usage `!sl400 clip last`.")
        }
    }

    private fun handleClips(parts: List<String>, config: AlertConfig): CommandResult {
        val mode = parts.getOrNull(2)?.lowercase()
        val value = parts.getOrNull(3)?.lowercase()
        if (mode != "since" || value == null) {
            return CommandResult(config, "SL400: Usage `!sl400 clips since 2h`.")
        }
        val durationMs = MatrixCommandParsing.parseDurationMs(value)
        return if (durationMs == null) {
            CommandResult(config, "SL400: Invalid duration `$value`.")
        } else {
            CommandResult(
                config,
                "SL400: fetching clips since $value",
                CommandAction.ClipsSince(durationMs, value)
            )
        }
    }
}
