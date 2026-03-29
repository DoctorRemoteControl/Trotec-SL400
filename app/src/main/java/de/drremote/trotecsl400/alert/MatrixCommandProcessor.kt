package de.drremote.trotecsl400.alert

class MatrixCommandProcessor {
    private val handlers: List<MatrixCommandHandler> = listOf(
        MatrixHelpCommandHandler(),
        MatrixConfigCommandHandler(),
        MatrixAudioCommandHandler(),
        MatrixExportCommandHandler(),
        MatrixReportCommandHandler(),
        MatrixGraphCommandHandler(),
        MatrixQueryCommandHandler(),
        MatrixClipCommandHandler()
    )

    fun process(
        message: String,
        senderUserId: String?,
        roomId: String?,
        config: AlertConfig
    ): CommandResult? {
        if (message.isBlank()) return null
        if (senderUserId == null) return null
        if (roomId == null) return null
        if (roomId != null && config.commandRoomId.isNotBlank() && roomId != config.commandRoomId) {
            return null
        }
        if (senderUserId != null && config.allowedSenders.isNotEmpty() &&
            senderUserId !in config.allowedSenders
        ) {
            return null
        }

        val trimmed = message.trim()
        if (!trimmed.startsWith("!sl400")) return null

        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 2) {
            return CommandResult(config, "SL400: Unknown command. Try `!sl400 status`.")
        }

        for (handler in handlers) {
            val result = handler.handle(parts, config)
            if (result != null) return result
        }

        return CommandResult(config, "SL400: Unknown command `${parts[1]}`.")
    }
}
