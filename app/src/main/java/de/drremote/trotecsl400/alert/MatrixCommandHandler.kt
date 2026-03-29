package de.drremote.trotecsl400.alert

interface MatrixCommandHandler {
    fun handle(parts: List<String>, config: AlertConfig): CommandResult?
}
