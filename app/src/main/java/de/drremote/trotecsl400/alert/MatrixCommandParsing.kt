package de.drremote.trotecsl400.alert

object MatrixCommandParsing {
    fun parseDurationMs(text: String): Long? {
        if (text.isBlank()) return null
        val unit = text.last()
        val number = text.dropLast(1).toLongOrNull() ?: return null
        return when (unit) {
            'm' -> number * 60_000L
            'h' -> number * 3_600_000L
            'd' -> number * 86_400_000L
            else -> null
        }
    }

    fun parseDateTimeMs(text: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getDefault()
                sdf.isLenient = false
                val date = sdf.parse(text) ?: continue
                return date.time
            } catch (_: Throwable) {
            }
        }
        return null
    }
}
