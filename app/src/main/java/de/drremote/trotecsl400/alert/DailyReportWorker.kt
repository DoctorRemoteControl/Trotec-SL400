package de.drremote.trotecsl400.alert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.drremote.trotecsl400.incident.IncidentRepository
import de.drremote.trotecsl400.matrix.HttpMatrixPublisher
import de.drremote.trotecsl400.matrix.MatrixSettingsRepository
import java.util.Calendar

class DailyReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val alertSettings = AlertSettingsRepository(applicationContext)
        val matrixSettings = MatrixSettingsRepository(applicationContext)
        val alertConfig = alertSettings.getConfig()
        val matrixConfig = matrixSettings.getConfig()

        val roomId = DailyReportScheduler.resolveRoomId(alertConfig, matrixConfig)
        if (!alertConfig.dailyReportEnabled || roomId.isBlank() || !matrixConfig.enabled) {
            DailyReportScheduler.cancel(applicationContext)
            return Result.success()
        }
        if (matrixConfig.homeserverBaseUrl.isBlank() || matrixConfig.accessToken.isBlank()) {
            DailyReportScheduler.cancel(applicationContext)
            return Result.success()
        }

        val incidentRepository = IncidentRepository(applicationContext)
        val start = startOfYesterdayMs()
        val end = startOfTodayMs()
        val incidents = incidentRepository.getIncidentsBetween(start, end)
        val label = "yesterday"
        val message = SummaryFormatter.buildSummaryMessage(incidents, label, audioRunning = null)

        return runCatching {
            val publisher = HttpMatrixPublisher()
            publisher.sendText(matrixConfig, roomId, message)
            if (alertConfig.dailyReportJsonEnabled) {
                val jsonFile = JsonExporter.writeIncidentsJson(applicationContext, incidents, label)
                try {
                    publisher.sendFile(
                        matrixConfig,
                        roomId,
                        jsonFile,
                        body = "SL400 incidents JSON ($label)",
                        mimeType = "application/json",
                        fileName = jsonFile.name
                    )
                } finally {
                    runCatching { jsonFile.delete() }
                }
            }
            if (alertConfig.dailyReportGraphEnabled && incidents.isNotEmpty()) {
                val primaryLabel = incidents.map { it.metricMode }.distinct().let { modes ->
                    if (modes.size == 1) {
                        IncidentFormatting.formatMetricMode(modes.first())
                    } else {
                        "Incident metric (mixed)"
                    }
                }
                val showSecondary = primaryLabel != "LAeq 5 min"
                val graph = IncidentGraphRenderer.render(
                    applicationContext,
                    incidents,
                    label,
                    hysteresisDb = alertConfig.hysteresisDb,
                    primaryLabel = primaryLabel,
                    secondaryLabel = "LAeq 5 min",
                    showSecondaryLine = showSecondary
                )
                try {
                    publisher.sendImage(
                        matrixConfig,
                        roomId,
                        graph.file,
                        body = "SL400 incidents graph ($label)",
                        mimeType = "image/png",
                        width = graph.width,
                        height = graph.height,
                        fileName = graph.file.name
                    )
                } finally {
                    runCatching { graph.file.delete() }
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                val msg = e.message.orEmpty()
                if (
                    msg.contains("401") ||
                    msg.contains("403") ||
                    msg.contains("Access token is missing") ||
                    msg.contains("Homeserver URL is invalid") ||
                    msg.contains("Room ID is missing")
                ) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        )
    }

    private fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfYesterdayMs(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
