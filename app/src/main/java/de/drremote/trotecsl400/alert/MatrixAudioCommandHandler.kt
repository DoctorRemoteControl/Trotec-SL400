package de.drremote.trotecsl400.alert

class MatrixAudioCommandHandler : MatrixCommandHandler {
    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        if (parts[1].lowercase() != "audio") return null
        val value = parts.getOrNull(2)?.lowercase()
        return when (value) {
            "start" -> CommandResult(
                config,
                "SL400: audio start requested",
                CommandAction.AudioStart
            )
            "stop" -> CommandResult(
                config,
                "SL400: audio stop requested",
                CommandAction.AudioStop
            )
            "status" -> CommandResult(
                config,
                "SL400: audio status",
                CommandAction.AudioStatus
            )
            else -> CommandResult(
                config,
                "SL400: Usage `!sl400 audio start|stop|status`."
            )
        }
    }
}
