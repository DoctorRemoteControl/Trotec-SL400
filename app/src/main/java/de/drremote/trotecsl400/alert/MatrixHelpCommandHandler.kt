package de.drremote.trotecsl400.alert

class MatrixHelpCommandHandler : MatrixCommandHandler {
    override fun handle(parts: List<String>, config: AlertConfig): CommandResult? {
        if (parts.size < 2) return null
        return when (parts[1].lowercase()) {
            "help" -> CommandResult(config, helpMessage())
            else -> null
        }
    }

    private fun helpMessage(): String {
        return buildString {
            append("SL400 commands:\n")
            append("!sl400 help\n")
            append("!sl400 status | config\n")
            append("!sl400 set threshold <db>\n")
            append("!sl400 set hysteresis <db>\n")
            append("!sl400 set metric live|laeq1|laeq5|laeq15|max1\n")
            append("!sl400 set mode crossing|periodic\n")
            append("!sl400 set interval <ms>\n")
            append("!sl400 set commandroom <roomId>\n")
            append("!sl400 set targetroom <roomId>\n")
            append("!sl400 set allow <@user:server>\n")
            append("!sl400 set deny <@user:server>\n")
            append("!sl400 set dailyreport true|false\n")
            append("!sl400 set reporttime HH:MM\n")
            append("!sl400 set reportroom <roomId>\n")
            append("!sl400 set reportjson true|false\n")
            append("!sl400 set reportgraph true|false\n")
            append("!sl400 set alerthint true|false\n")
            append("!sl400 summary today|yesterday\n")
            append("!sl400 summary since <duration>\n")
            append("!sl400 incidents today|yesterday\n")
            append("!sl400 incidents since <duration>\n")
            append("!sl400 incidents between <start> <end>\n")
            append("!sl400 json today|yesterday\n")
            append("!sl400 json since <duration>\n")
            append("!sl400 json ... cliponly\n")
            append("!sl400 json ... metric=<live|laeq1|laeq5|laeq15|max1>\n")
            append("!sl400 report now|today|yesterday\n")
            append("!sl400 report since <duration>\n")
            append("report uses reportjson/reportgraph settings\n")
            append("!sl400 graph today|yesterday\n")
            append("!sl400 graph since <duration>\n")
            append("!sl400 clip last\n")
            append("!sl400 clip incident <id>\n")
            append("!sl400 audio status|start|stop")
        }
    }
}
