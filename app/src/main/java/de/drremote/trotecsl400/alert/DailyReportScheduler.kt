package de.drremote.trotecsl400.alert

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyReportScheduler {
    private const val UNIQUE_WORK_NAME = "sl400_daily_report"

    fun schedule(context: Context, alertConfig: AlertConfig, matrixConfig: de.drremote.trotecsl400.matrix.MatrixConfig) {
        val roomId = resolveRoomId(alertConfig, matrixConfig)
        if (!alertConfig.dailyReportEnabled || roomId.isBlank() || !matrixConfig.enabled) {
            cancel(context)
            return
        }
        if (matrixConfig.homeserverBaseUrl.isBlank() || matrixConfig.accessToken.isBlank()) {
            cancel(context)
            return
        }

        val delayMs = computeDelayMs(alertConfig.dailyReportHour, alertConfig.dailyReportMinute)
        val request = PeriodicWorkRequestBuilder<DailyReportWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun resolveRoomId(
        alertConfig: AlertConfig,
        matrixConfig: de.drremote.trotecsl400.matrix.MatrixConfig
    ): String {
        return alertConfig.dailyReportRoomId
            .ifBlank { alertConfig.targetRoomId }
            .ifBlank { matrixConfig.roomId }
    }

    private fun computeDelayMs(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DATE, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
