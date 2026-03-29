package de.drremote.trotecsl400.alert

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.alertDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "alert_settings"
)

class AlertSettingsRepository(private val context: Context) {

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val thresholdDb = doublePreferencesKey("threshold_db")
        val hysteresisDb = doublePreferencesKey("hysteresis_db")
        val minSendIntervalMs = longPreferencesKey("min_send_interval_ms")
        val sendMode = stringPreferencesKey("send_mode")
        val metricMode = stringPreferencesKey("metric_mode")
        val allowedSendersCsv = stringPreferencesKey("allowed_senders_csv")
        val commandRoomId = stringPreferencesKey("command_room_id")
        val targetRoomId = stringPreferencesKey("target_room_id")
        val alertHintFollowupEnabled = booleanPreferencesKey("alert_hint_followup_enabled")
        val dailyReportEnabled = booleanPreferencesKey("daily_report_enabled")
        val dailyReportHour = longPreferencesKey("daily_report_hour")
        val dailyReportMinute = longPreferencesKey("daily_report_minute")
        val dailyReportRoomId = stringPreferencesKey("daily_report_room_id")
        val dailyReportJsonEnabled = booleanPreferencesKey("daily_report_json_enabled")
        val dailyReportGraphEnabled = booleanPreferencesKey("daily_report_graph_enabled")
    }

    val config: Flow<AlertConfig> = context.alertDataStore.data.map { prefs ->
        val mode = when (prefs[Keys.sendMode]) {
            SendMode.PERIODIC_WHILE_ABOVE.name -> SendMode.PERIODIC_WHILE_ABOVE
            else -> SendMode.CROSSING_ONLY
        }
        val metricMode = runCatching {
            MetricMode.valueOf(prefs[Keys.metricMode] ?: MetricMode.LAEQ_5_MIN.name)
        }.getOrDefault(MetricMode.LAEQ_5_MIN)
        val allowed = prefs[Keys.allowedSendersCsv]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        AlertConfig(
            enabled = prefs[Keys.enabled] ?: false,
            thresholdDb = prefs[Keys.thresholdDb] ?: 70.0,
            hysteresisDb = prefs[Keys.hysteresisDb] ?: 2.0,
            minSendIntervalMs = prefs[Keys.minSendIntervalMs] ?: 60_000,
            sendMode = mode,
            metricMode = metricMode,
            allowedSenders = allowed,
            commandRoomId = prefs[Keys.commandRoomId] ?: "",
            targetRoomId = prefs[Keys.targetRoomId] ?: "",
            alertHintFollowupEnabled = prefs[Keys.alertHintFollowupEnabled] ?: true,
            dailyReportEnabled = prefs[Keys.dailyReportEnabled] ?: false,
            dailyReportHour = (prefs[Keys.dailyReportHour] ?: 9L).toInt(),
            dailyReportMinute = (prefs[Keys.dailyReportMinute] ?: 0L).toInt(),
            dailyReportRoomId = prefs[Keys.dailyReportRoomId] ?: "",
            dailyReportJsonEnabled = prefs[Keys.dailyReportJsonEnabled] ?: true,
            dailyReportGraphEnabled = prefs[Keys.dailyReportGraphEnabled] ?: true
        )
    }

    suspend fun setEnabled(value: Boolean) {
        context.alertDataStore.edit { it[Keys.enabled] = value }
    }

    suspend fun setThresholdDb(value: Double) {
        context.alertDataStore.edit { it[Keys.thresholdDb] = value }
    }

    suspend fun setHysteresisDb(value: Double) {
        context.alertDataStore.edit { it[Keys.hysteresisDb] = value }
    }

    suspend fun setMinSendIntervalMs(value: Long) {
        context.alertDataStore.edit { it[Keys.minSendIntervalMs] = value }
    }

    suspend fun setSendMode(value: SendMode) {
        context.alertDataStore.edit { it[Keys.sendMode] = value.name }
    }

    suspend fun setMetricMode(value: MetricMode) {
        context.alertDataStore.edit { it[Keys.metricMode] = value.name }
    }

    suspend fun setAllowedSendersCsv(value: String) {
        context.alertDataStore.edit { it[Keys.allowedSendersCsv] = value }
    }

    suspend fun setCommandRoomId(value: String) {
        context.alertDataStore.edit { it[Keys.commandRoomId] = value }
    }

    suspend fun setTargetRoomId(value: String) {
        context.alertDataStore.edit { it[Keys.targetRoomId] = value }
    }

    suspend fun setAlertHintFollowupEnabled(value: Boolean) {
        context.alertDataStore.edit { it[Keys.alertHintFollowupEnabled] = value }
    }

    suspend fun setDailyReportEnabled(value: Boolean) {
        context.alertDataStore.edit { it[Keys.dailyReportEnabled] = value }
    }

    suspend fun setDailyReportTime(hour: Int, minute: Int) {
        context.alertDataStore.edit {
            it[Keys.dailyReportHour] = hour.toLong()
            it[Keys.dailyReportMinute] = minute.toLong()
        }
    }

    suspend fun setDailyReportRoomId(value: String) {
        context.alertDataStore.edit { it[Keys.dailyReportRoomId] = value }
    }

    suspend fun setDailyReportJsonEnabled(value: Boolean) {
        context.alertDataStore.edit { it[Keys.dailyReportJsonEnabled] = value }
    }

    suspend fun setDailyReportGraphEnabled(value: Boolean) {
        context.alertDataStore.edit { it[Keys.dailyReportGraphEnabled] = value }
    }

    suspend fun setConfig(cfg: AlertConfig) {
        context.alertDataStore.edit {
            it[Keys.enabled] = cfg.enabled
            it[Keys.thresholdDb] = cfg.thresholdDb
            it[Keys.hysteresisDb] = cfg.hysteresisDb
            it[Keys.minSendIntervalMs] = cfg.minSendIntervalMs
            it[Keys.sendMode] = cfg.sendMode.name
            it[Keys.metricMode] = cfg.metricMode.name
            it[Keys.allowedSendersCsv] = cfg.allowedSenders.joinToString(",")
            it[Keys.commandRoomId] = cfg.commandRoomId
            it[Keys.targetRoomId] = cfg.targetRoomId
            it[Keys.alertHintFollowupEnabled] = cfg.alertHintFollowupEnabled
            it[Keys.dailyReportEnabled] = cfg.dailyReportEnabled
            it[Keys.dailyReportHour] = cfg.dailyReportHour.toLong()
            it[Keys.dailyReportMinute] = cfg.dailyReportMinute.toLong()
            it[Keys.dailyReportRoomId] = cfg.dailyReportRoomId
            it[Keys.dailyReportJsonEnabled] = cfg.dailyReportJsonEnabled
            it[Keys.dailyReportGraphEnabled] = cfg.dailyReportGraphEnabled
        }
    }

    suspend fun getConfig(): AlertConfig {
        return config.first()
    }
}
