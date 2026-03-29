package de.drremote.trotecsl400.sl400

import android.app.Application
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.drremote.trotecsl400.alert.AlertConfig
import de.drremote.trotecsl400.alert.AlertSettingsRepository
import de.drremote.trotecsl400.alert.AlarmEvaluator
import de.drremote.trotecsl400.alert.MatrixCommandProcessor
import de.drremote.trotecsl400.alert.CommandAction
import de.drremote.trotecsl400.Sl400ForegroundService
import de.drremote.trotecsl400.audio.AlarmAudioCaptureCoordinator
import de.drremote.trotecsl400.incident.IncidentRecord
import de.drremote.trotecsl400.incident.IncidentRepository
import de.drremote.trotecsl400.alert.MetricMode
import de.drremote.trotecsl400.alert.DailyReportScheduler
import de.drremote.trotecsl400.alert.SummaryFormatter
import de.drremote.trotecsl400.alert.JsonExporter
import de.drremote.trotecsl400.alert.IncidentGraphRenderer
import de.drremote.trotecsl400.audio.AudioHintAnalyzer
import de.drremote.trotecsl400.audio.AudioHintResult
import de.drremote.trotecsl400.alert.IncidentFormatting
import de.drremote.trotecsl400.alert.SendMode
import de.drremote.trotecsl400.matrix.HttpMatrixPublisher
import de.drremote.trotecsl400.matrix.MatrixConfig
import de.drremote.trotecsl400.matrix.MatrixSettingsRepository
import de.drremote.trotecsl400.matrix.MatrixSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedHashMap

data class Sl400UiState(
    val devices: List<UsbDevice> = emptyList(),
    val connectedDeviceId: Int? = null,
    val connected: Boolean = false,
    val lastSample: Sl400Sample? = null,
    val samples: List<Sl400Sample> = emptyList(),
    val error: String? = null,
    val matrixConfig: MatrixConfig = MatrixConfig(),
    val matrixDraft: MatrixDraft = MatrixDraft(),
    val matrixDraftDirty: Boolean = false,
    val matrixLastStatus: String? = null,
    val alertConfig: AlertConfig = AlertConfig(),
    val alertDraft: AlertDraft = AlertDraft(),
    val alertDraftDirty: Boolean = false,
    val audioCaptureStatus: String = "Idle",
    val audioCaptureDevice: String = "None",
    val audioCaptureSampleRate: Int = 0,
    val audioCaptureBufferSeconds: Int = 0,
    val audioCaptureLastClipPath: String = "",
    val audioCaptureRunning: Boolean = false
)

data class MatrixDraft(
    val homeserverBaseUrl: String = "",
    val accessToken: String = "",
    val roomId: String = ""
)

data class AlertDraft(
    val enabled: Boolean = false,
    val thresholdText: String = "70.0",
    val hysteresisText: String = "2.0",
    val intervalText: String = "60000",
    val sendMode: SendMode = SendMode.CROSSING_ONLY,
    val metricMode: MetricMode = MetricMode.LAEQ_5_MIN,
    val allowedSendersCsv: String = "",
    val commandRoomId: String = "",
    val targetRoomId: String = "",
    val alertHintFollowupEnabled: Boolean = true,
    val dailyReportEnabled: Boolean = false,
    val dailyReportHourText: String = "9",
    val dailyReportMinuteText: String = "0",
    val dailyReportRoomId: String = "",
    val dailyReportJsonEnabled: Boolean = true,
    val dailyReportGraphEnabled: Boolean = true
)

class Sl400ViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Sl400Repository(app, viewModelScope)
    private val matrixSettings = MatrixSettingsRepository(app)
    private val matrixPublisher = HttpMatrixPublisher()
    private val alertSettings = AlertSettingsRepository(app)
    private val alarmEvaluator = AlarmEvaluator()
    private val metricsEngine = AcousticMetricsEngine()
    private val audioCoordinator = AlarmAudioCaptureCoordinator(app)
    private val incidentRepository = IncidentRepository(app)
    private val commandProcessor = MatrixCommandProcessor()
    private val matrixSyncClient = MatrixSyncClient()
    private val alertSendLock = Any()
    private val sentAlertIds = LinkedHashMap<String, Long>()
    private val inFlightAlertIds = mutableSetOf<String>()

    private var syncJob: Job? = null
    private var syncKey: String? = null

    private val _ui = MutableStateFlow(Sl400UiState())
    val ui: StateFlow<Sl400UiState> = _ui

    init {
        viewModelScope.launch {
            repo.samples.collect { sample ->
                val matrixCfg = _ui.value.matrixConfig
                val alertCfg = _ui.value.alertConfig
                val metrics = metricsEngine.addSample(sample, alertCfg.thresholdDb)

                if (matrixCfg.enabled && alarmEvaluator.shouldSend(metrics, alertCfg)) {
                    val targetRoomId = alertCfg.targetRoomId.ifBlank { matrixCfg.roomId }
                    val targetCfg = matrixCfg.copy(roomId = targetRoomId)
                    val alertId = sample.timestampMs.toString()
                    val hintRoomId = targetRoomId.ifBlank { matrixCfg.roomId }
                    val incident = IncidentRecord(
                        incidentId = alertId,
                        timestampMs = sample.timestampMs,
                        roomId = targetRoomId,
                        metricMode = alertCfg.metricMode.name,
                        metricValue = metricValue(metrics, alertCfg.metricMode),
                        thresholdDb = alertCfg.thresholdDb,
                        laEq1Min = metrics.laEq1Min,
                        laEq5Min = metrics.laEq5Min,
                        laEq15Min = metrics.laEq15Min,
                        maxDb1Min = metrics.maxDb1Min,
                        timeAboveThresholdMs1Min = metrics.timeAboveThresholdMs1Min,
                        clipPath = "",
                        clipUploaded = false,
                        mxcUrl = null,
                        audioHint = null
                    )
                    viewModelScope.launch { incidentRepository.add(incident) }
                    viewModelScope.launch {
                        incidentRepository.cleanup(
                            retentionMs = 14L * 24 * 60 * 60 * 1000,
                            maxRecords = 2000,
                            maxClips = 200
                        )
                    }
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(ALERT_HINT_TIMEOUT_MS)
                        sendAlertIfNotSent(alertId, targetCfg, sample, metrics, alertCfg, null)
                    }
                    audioCoordinator.captureIncidentClip(alertId) { status ->
                        _ui.update {
                            it.copy(
                                audioCaptureStatus = status.message,
                                audioCaptureDevice = status.deviceInfo,
                                audioCaptureSampleRate = status.sampleRate,
                                audioCaptureBufferSeconds = status.bufferSeconds,
                                audioCaptureLastClipPath = status.lastClipPath,
                                audioCaptureRunning = status.isRecording
                            )
                        }
                        if (status.lastClipPath.isNotBlank()) {
                            viewModelScope.launch {
                                incidentRepository.updateClip(alertId, status.lastClipPath)
                                val hint = withContext(Dispatchers.IO) {
                                    AudioHintAnalyzer.analyzeWav(File(status.lastClipPath))
                                }
                                incidentRepository.updateAudioHint(alertId, hint?.label)
                                val sentWithHint = sendAlertIfNotSent(
                                    alertId,
                                    targetCfg,
                                    sample,
                                    metrics,
                                    alertCfg,
                                    hint?.label
                                )
                                if (!sentWithHint &&
                                    hint != null &&
                                    _ui.value.alertConfig.alertHintFollowupEnabled
                                ) {
                                    val msg = buildAudioHintMessage(alertId, hint)
                                    runCatching {
                                        matrixPublisher.sendText(matrixCfg, hintRoomId, msg)
                                    }
                                }
                            }
                        }
                    }
                }
                _ui.update {
                    it.copy(
                        connected = true,
                        lastSample = sample,
                        samples = (listOf(sample) + it.samples).take(20),
                        error = null
                    )
                }
            }
        }

        viewModelScope.launch {
            repo.errors.collect { msg ->
                repo.close()
                alarmEvaluator.reset()
                metricsEngine.reset()
                _ui.update {
                    it.copy(
                        connected = false,
                        connectedDeviceId = null,
                        error = msg
                    )
                }
            }
        }

        viewModelScope.launch {
            matrixSettings.config.collect { config ->
                _ui.update { state ->
                    val nextDraft = if (state.matrixDraftDirty) {
                        state.matrixDraft
                    } else {
                        matrixDraftFromConfig(config)
                    }
                    state.copy(
                        matrixConfig = config,
                        matrixDraft = nextDraft
                    )
                }
                updateSync()
                updateDailyReport()
            }
        }

        viewModelScope.launch {
            alertSettings.config.collect { config ->
                _ui.update { state ->
                    val nextDraft = if (state.alertDraftDirty) {
                        state.alertDraft
                    } else {
                        alertDraftFromConfig(config)
                    }
                    state.copy(
                        alertConfig = config,
                        alertDraft = nextDraft
                    )
                }
                updateSync()
                updateDailyReport()
            }
        }
    }

    fun refreshDevices() {
        val devices = repo.findCandidateDevices()
        _ui.update { it.copy(devices = devices) }
    }

    fun hasPermission(device: UsbDevice): Boolean = repo.hasPermission(device)

    fun connect(device: UsbDevice) {
        viewModelScope.launch {
            _ui.update { it.copy(error = null) }
            alarmEvaluator.reset()
            metricsEngine.reset()

            runCatching {
                withContext(Dispatchers.IO) {
                    repo.open(device)
                }
            }.onSuccess {
                _ui.update {
                    it.copy(
                        connected = true,
                        connectedDeviceId = device.deviceId,
                        error = null
                    )
                }
                Sl400ForegroundService.start(getApplication())
            }.onFailure { e ->
                Log.e("SL400", "connect failed", e)
                _ui.update {
                    it.copy(
                        connected = false,
                        connectedDeviceId = null,
                        error = e.message ?: (e::class.java.simpleName ?: "Failed to connect")
                    )
                }
            }
        }
    }

    fun setMatrixHomeserver(url: String) {
        viewModelScope.launch { matrixSettings.setHomeserver(url) }
    }

    fun setMatrixAccessToken(token: String) {
        viewModelScope.launch { matrixSettings.setAccessToken(token) }
    }

    fun setMatrixRoomId(roomId: String) {
        viewModelScope.launch { matrixSettings.setRoomId(roomId) }
    }

    fun setMatrixEnabled(enabled: Boolean) {
        viewModelScope.launch { matrixSettings.setEnabled(enabled) }
    }

    fun updateMatrixDraft(update: (MatrixDraft) -> MatrixDraft) {
        _ui.update { state ->
            state.copy(
                matrixDraft = update(state.matrixDraft),
                matrixDraftDirty = true
            )
        }
    }

    fun saveMatrixDraft() {
        val draft = _ui.value.matrixDraft
        viewModelScope.launch {
            matrixSettings.setHomeserver(draft.homeserverBaseUrl)
            matrixSettings.setAccessToken(draft.accessToken)
            matrixSettings.setRoomId(draft.roomId)
            _ui.update { state -> state.copy(matrixDraftDirty = false) }
        }
    }

    fun sendMatrixTestMessage() {
        val draft = _ui.value.matrixDraft
        val config = _ui.value.matrixConfig.copy(
            homeserverBaseUrl = draft.homeserverBaseUrl,
            accessToken = draft.accessToken,
            roomId = draft.roomId
        )
        viewModelScope.launch {
            val error = when {
                draft.homeserverBaseUrl.isBlank() -> "Homeserver URL is missing"
                draft.accessToken.isBlank() -> "Access token is missing"
                draft.roomId.isBlank() -> "Room ID is missing"
                else -> null
            }

            if (error != null) {
                _ui.update { it.copy(matrixLastStatus = error) }
                return@launch
            }

            runCatching { matrixPublisher.sendTestMessage(config) }
                .onSuccess {
                    _ui.update { it.copy(matrixLastStatus = "Test message sent") }
                }
                .onFailure { e ->
                    _ui.update { it.copy(matrixLastStatus = e.message ?: "Matrix error") }
                }
        }
    }

    fun setAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { alertSettings.setEnabled(enabled) }
    }

    fun setAlertThresholdDb(value: Double) {
        viewModelScope.launch { alertSettings.setThresholdDb(value) }
    }

    fun setAlertHysteresisDb(value: Double) {
        viewModelScope.launch { alertSettings.setHysteresisDb(value) }
    }

    fun setAlertMinSendIntervalMs(value: Long) {
        viewModelScope.launch { alertSettings.setMinSendIntervalMs(value) }
    }

    fun setAlertSendMode(value: de.drremote.trotecsl400.alert.SendMode) {
        viewModelScope.launch { alertSettings.setSendMode(value) }
    }

    fun setAlertAllowedSendersCsv(value: String) {
        viewModelScope.launch { alertSettings.setAllowedSendersCsv(value) }
    }

    fun setAlertCommandRoomId(value: String) {
        viewModelScope.launch { alertSettings.setCommandRoomId(value) }
    }

    fun setAlertTargetRoomId(value: String) {
        viewModelScope.launch { alertSettings.setTargetRoomId(value) }
    }

    fun updateAlertDraft(update: (AlertDraft) -> AlertDraft) {
        _ui.update { state ->
            state.copy(
                alertDraft = update(state.alertDraft),
                alertDraftDirty = true
            )
        }
    }

    fun saveAlertDraft() {
        val draft = _ui.value.alertDraft
        val current = _ui.value.alertConfig
        val threshold = draft.thresholdText.toDoubleOrNull() ?: current.thresholdDb
        val hysteresis = draft.hysteresisText.toDoubleOrNull() ?: current.hysteresisDb
        val interval = draft.intervalText.toLongOrNull() ?: current.minSendIntervalMs
        val reportHour = draft.dailyReportHourText.toIntOrNull()?.coerceIn(0, 23)
            ?: current.dailyReportHour
        val reportMinute = draft.dailyReportMinuteText.toIntOrNull()?.coerceIn(0, 59)
            ?: current.dailyReportMinute
        val allowed = draft.allowedSendersCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val cfg = AlertConfig(
            enabled = draft.enabled,
            thresholdDb = threshold,
            hysteresisDb = hysteresis,
            minSendIntervalMs = interval,
            sendMode = draft.sendMode,
            metricMode = draft.metricMode,
            allowedSenders = allowed,
            commandRoomId = draft.commandRoomId,
            targetRoomId = draft.targetRoomId,
            alertHintFollowupEnabled = draft.alertHintFollowupEnabled,
            dailyReportEnabled = draft.dailyReportEnabled,
            dailyReportHour = reportHour,
            dailyReportMinute = reportMinute,
            dailyReportRoomId = draft.dailyReportRoomId,
            dailyReportJsonEnabled = draft.dailyReportJsonEnabled,
            dailyReportGraphEnabled = draft.dailyReportGraphEnabled
        )
        saveAlertConfig(cfg)
        _ui.update { state ->
            state.copy(
                alertDraftDirty = false,
                alertDraft = alertDraftFromConfig(cfg)
            )
        }
    }

    fun startAudioCapture(bufferSeconds: Int = 30) {
        audioCoordinator.start(bufferSeconds) { status ->
            _ui.update {
                it.copy(
                    audioCaptureStatus = status.message,
                    audioCaptureDevice = status.deviceInfo,
                    audioCaptureSampleRate = status.sampleRate,
                    audioCaptureBufferSeconds = status.bufferSeconds,
                    audioCaptureLastClipPath = status.lastClipPath,
                    audioCaptureRunning = status.isRecording
                )
            }
        }
    }

    fun stopAudioCapture() {
        audioCoordinator.stop { status ->
            _ui.update {
                it.copy(
                    audioCaptureStatus = status.message,
                    audioCaptureDevice = status.deviceInfo,
                    audioCaptureSampleRate = status.sampleRate,
                    audioCaptureBufferSeconds = status.bufferSeconds,
                    audioCaptureLastClipPath = status.lastClipPath,
                    audioCaptureRunning = status.isRecording
                )
            }
        }
    }

    fun onPermissionDenied() {
        _ui.update { it.copy(error = "USB permission denied") }
    }

    fun onDeviceDetached(device: UsbDevice) {
        val connectedId = _ui.value.connectedDeviceId
        if (connectedId != null && connectedId == device.deviceId) {
            repo.close()
            alarmEvaluator.reset()
            metricsEngine.reset()
            _ui.update {
                it.copy(
                    connected = false,
                    connectedDeviceId = null
                )
            }
            Sl400ForegroundService.stop(getApplication())
        }
    }

    fun disconnect() {
        repo.close()
        alarmEvaluator.reset()
        metricsEngine.reset()
        _ui.update {
            it.copy(
                connected = false,
                connectedDeviceId = null
            )
        }
        Sl400ForegroundService.stop(getApplication())
    }

    override fun onCleared() {
        syncJob?.cancel()
        repo.close()
        audioCoordinator.stop { }
        Sl400ForegroundService.stop(getApplication())
        super.onCleared()
    }

    private fun updateSync() {
        val matrixCfg = _ui.value.matrixConfig
        val alertCfg = _ui.value.alertConfig

        val shouldRun = matrixCfg.enabled &&
            matrixCfg.homeserverBaseUrl.isNotBlank() &&
            matrixCfg.accessToken.isNotBlank() &&
            alertCfg.commandRoomId.isNotBlank()

        val key = if (shouldRun) {
            "${matrixCfg.homeserverBaseUrl}|${matrixCfg.accessToken}|${alertCfg.commandRoomId}"
        } else {
            null
        }

        if (!shouldRun) {
            syncJob?.cancel()
            syncJob = null
            syncKey = null
            return
        }

        if (syncJob != null && syncKey == key) return

        syncJob?.cancel()
        syncKey = key
        syncJob = viewModelScope.launch {
            while (kotlin.coroutines.coroutineContext.isActive) {
                runCatching {
                    val ownUserId = matrixSyncClient.whoAmI(matrixCfg)
                    val initialSince = matrixSettings.getSyncToken()
                    matrixSyncClient.syncLoop(
                        config = matrixCfg,
                        commandRoomId = alertCfg.commandRoomId,
                        ignoreUserId = ownUserId,
                        initialSince = initialSince,
                        onNextBatch = { token -> matrixSettings.setSyncToken(token) }
                    ) { msg ->
                        val current = _ui.value.alertConfig
                        val result = commandProcessor.process(
                            message = msg.body,
                            senderUserId = msg.sender,
                            roomId = msg.roomId,
                            config = current
                        ) ?: return@syncLoop

                        if (result.updatedConfig != current) {
                            applyAlertConfig(result.updatedConfig)
                        }
                        if (msg.roomId.isNotBlank()) {
                            if (result.action != null) {
                                handleCommandAction(result.action, msg.roomId, matrixCfg)
                            } else {
                                matrixPublisher.sendText(matrixCfg, msg.roomId, result.responseMessage)
                            }
                        }
                    }
                }.onFailure { e ->
                    _ui.update { it.copy(matrixLastStatus = e.message ?: "Matrix sync error") }
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    private fun applyAlertConfig(cfg: AlertConfig) {
        alarmEvaluator.reset()
        viewModelScope.launch { alertSettings.setConfig(cfg) }
    }

    fun saveAlertConfig(cfg: AlertConfig) {
        alarmEvaluator.reset()
        viewModelScope.launch { alertSettings.setConfig(cfg) }
    }

    private suspend fun handleCommandAction(
        action: CommandAction,
        roomId: String,
        matrixCfg: MatrixConfig
    ) {
        when (action) {
            CommandAction.AudioStart -> {
                startAudioCapture()
                matrixPublisher.sendText(matrixCfg, roomId, "SL400: audio start requested")
            }
            CommandAction.AudioStop -> {
                stopAudioCapture()
                matrixPublisher.sendText(matrixCfg, roomId, "SL400: audio stop requested")
            }
            CommandAction.AudioStatus -> {
                val status = buildAudioStatus()
                matrixPublisher.sendText(matrixCfg, roomId, status)
            }
            is CommandAction.IncidentsSince -> {
                val incidents = incidentRepository.getIncidentsSince(action.durationMs)
                val body = buildIncidentsMessage(incidents, action.label)
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            is CommandAction.IncidentsBetween -> {
                val incidents = incidentRepository.getIncidentsBetween(
                    action.startMs,
                    action.endMs
                )
                val body = buildIncidentsMessage(incidents, action.label)
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            CommandAction.IncidentsToday -> {
                val start = startOfTodayMs()
                val end = System.currentTimeMillis()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                val body = buildIncidentsMessage(incidents, "today")
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            CommandAction.IncidentsYesterday -> {
                val start = startOfYesterdayMs()
                val end = startOfTodayMs()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                val body = buildIncidentsMessage(incidents, "yesterday")
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            is CommandAction.ClipsSince -> {
                val incidents = incidentRepository.getClipsSince(action.durationMs)
                val body = buildClipsMessage(incidents, action.label)
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            CommandAction.ClipLast -> {
                val incident = incidentRepository.getLastClipIncident()
                if (incident == null) {
                    matrixPublisher.sendText(matrixCfg, roomId, "SL400: No clip available.")
                    return
                }
                sendIncidentClip(matrixCfg, roomId, incident)
            }
            is CommandAction.ClipIncident -> {
                val incident = incidentRepository.getIncidentById(action.incidentId)
                if (incident == null) {
                    matrixPublisher.sendText(matrixCfg, roomId, "SL400: Incident not found.")
                    return
                }
                sendIncidentClip(matrixCfg, roomId, incident)
            }
            is CommandAction.SummarySince -> {
                val incidents = incidentRepository.getIncidentsSince(action.durationMs)
                val body = buildSummaryMessage(incidents, action.label)
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            CommandAction.SummaryToday -> {
                val startOfDayMs = startOfTodayMs()
                val incidents = incidentRepository.getIncidentsBetween(
                    startOfDayMs,
                    System.currentTimeMillis()
                )
                val body = buildSummaryMessage(incidents, "today")
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            CommandAction.SummaryYesterday -> {
                val start = startOfYesterdayMs()
                val end = startOfTodayMs()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                val body = buildSummaryMessage(incidents, "yesterday")
                matrixPublisher.sendText(matrixCfg, roomId, body)
            }
            is CommandAction.JsonSince -> {
                val incidents = incidentRepository.getIncidentsSince(action.durationMs)
                sendJsonExport(
                    matrixCfg,
                    roomId,
                    filterJsonIncidents(incidents, action.clipOnly, action.metricMode),
                    buildJsonLabel("since ${action.label}", action.clipOnly, action.metricMode)
                )
            }
            is CommandAction.JsonToday -> {
                val start = startOfTodayMs()
                val end = System.currentTimeMillis()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                sendJsonExport(
                    matrixCfg,
                    roomId,
                    filterJsonIncidents(incidents, action.clipOnly, action.metricMode),
                    buildJsonLabel("today", action.clipOnly, action.metricMode)
                )
            }
            is CommandAction.JsonYesterday -> {
                val start = startOfYesterdayMs()
                val end = startOfTodayMs()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                sendJsonExport(
                    matrixCfg,
                    roomId,
                    filterJsonIncidents(incidents, action.clipOnly, action.metricMode),
                    buildJsonLabel("yesterday", action.clipOnly, action.metricMode)
                )
            }
            CommandAction.ReportNow -> {
                val start = startOfTodayMs()
                val end = System.currentTimeMillis()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                val label = buildReportLabel()
                val body = buildSummaryMessage(incidents, label)
                matrixPublisher.sendText(matrixCfg, roomId, body)
                sendReportExtras(matrixCfg, roomId, incidents, label)
            }
            CommandAction.ReportToday -> {
                val start = startOfTodayMs()
                val end = System.currentTimeMillis()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                val body = buildSummaryMessage(incidents, "today")
                matrixPublisher.sendText(matrixCfg, roomId, body)
                sendReportExtras(matrixCfg, roomId, incidents, "today")
            }
            CommandAction.ReportYesterday -> {
                val start = startOfYesterdayMs()
                val end = startOfTodayMs()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                val body = buildSummaryMessage(incidents, "yesterday")
                matrixPublisher.sendText(matrixCfg, roomId, body)
                sendReportExtras(matrixCfg, roomId, incidents, "yesterday")
            }
            is CommandAction.ReportSince -> {
                val incidents = incidentRepository.getIncidentsSince(action.durationMs)
                val body = buildSummaryMessage(incidents, "since ${action.label}")
                matrixPublisher.sendText(matrixCfg, roomId, body)
                sendReportExtras(matrixCfg, roomId, incidents, "since ${action.label}")
            }
            is CommandAction.GraphSince -> {
                val incidents = incidentRepository.getIncidentsSince(action.durationMs)
                sendReportGraph(matrixCfg, roomId, incidents, "since ${action.label}")
            }
            CommandAction.GraphToday -> {
                val start = startOfTodayMs()
                val end = System.currentTimeMillis()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                sendReportGraph(matrixCfg, roomId, incidents, "today")
            }
            CommandAction.GraphYesterday -> {
                val start = startOfYesterdayMs()
                val end = startOfTodayMs()
                val incidents = incidentRepository.getIncidentsBetween(start, end)
                sendReportGraph(matrixCfg, roomId, incidents, "yesterday")
            }
        }
    }

    private fun updateDailyReport() {
        DailyReportScheduler.schedule(
            getApplication(),
            _ui.value.alertConfig,
            _ui.value.matrixConfig
        )
    }

    private fun buildIncidentsMessage(
        incidents: List<IncidentRecord>,
        label: String
    ): String {
        if (incidents.isEmpty()) return "SL400 incidents ($label): none"
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val lines = incidents.sortedBy { it.timestampMs }.mapIndexed { index, incident ->
            val time = formatter.format(Date(incident.timestampMs))
            val metric = incident.metricValue?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
            val clip = if (incident.clipPath.isNotBlank()) "yes" else "no"
            "${index + 1}) $time | id=${incident.incidentId} | " +
                "${IncidentFormatting.formatMetricMode(incident.metricMode)} $metric dB | threshold " +
                String.format(Locale.US, "%.1f", incident.thresholdDb) + " | clip=$clip"
        }
        return "SL400 incidents ($label):\n" + lines.joinToString("\n")
    }

    private suspend fun sendIncidentClip(
        matrixCfg: MatrixConfig,
        roomId: String,
        incident: IncidentRecord
    ) {
        val caption = buildClipCaption(incident)
        if (!incident.mxcUrl.isNullOrBlank()) {
            matrixPublisher.sendAudioByMxcUrl(
                matrixCfg,
                roomId,
                incident.mxcUrl,
                caption,
                mimeType = "audio/wav",
                sizeBytes = null
            )
            return
        }

        val file = File(incident.clipPath)
        if (!file.exists()) {
            matrixPublisher.sendText(matrixCfg, roomId, "SL400: Clip file missing.")
            return
        }

        val fileName = buildClipFileName(incident)
        val upload = matrixPublisher.sendAudioClip(matrixCfg, roomId, file, caption, fileName)
        incidentRepository.markUploaded(incident.incidentId, upload.contentUri)
    }

    private fun buildClipCaption(incident: IncidentRecord): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val time = formatter.format(Date(incident.timestampMs))
        return "SL400 clip ${incident.incidentId} @ $time"
    }

    private fun buildClipFileName(incident: IncidentRecord): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val time = formatter.format(Date(incident.timestampMs))
        return "sl400_incident_${incident.incidentId}_$time.wav"
    }

    private fun buildClipsMessage(
        incidents: List<IncidentRecord>,
        label: String
    ): String {
        if (incidents.isEmpty()) return "SL400 clips since $label: none"
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val lines = incidents.sortedBy { it.timestampMs }.mapIndexed { index, incident ->
            val time = formatter.format(Date(incident.timestampMs))
            val metric = incident.metricValue?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
            "${index + 1}) $time | ${incident.incidentId} | " +
                "${IncidentFormatting.formatMetricMode(incident.metricMode)} $metric dB | clip=yes"
        }
        return "SL400 clips since $label:\n" + lines.joinToString("\n")
    }

    private fun buildSummaryMessage(
        incidents: List<IncidentRecord>,
        label: String
    ): String {
        return SummaryFormatter.buildSummaryMessage(
            incidents = incidents,
            label = label,
            audioRunning = _ui.value.audioCaptureRunning
        )
    }

    private fun buildAudioStatus(): String {
        val state = _ui.value
        val rate = if (state.audioCaptureSampleRate > 0) {
            "${state.audioCaptureSampleRate} Hz"
        } else {
            "0"
        }
        val buffer = if (state.audioCaptureBufferSeconds > 0) {
            "${state.audioCaptureBufferSeconds}s"
        } else {
            "0"
        }
        return "SL400 audio: running=${state.audioCaptureRunning}, " +
            "device=${state.audioCaptureDevice}, rate=$rate, buffer=$buffer"
    }

    private suspend fun sendJsonExport(
        matrixCfg: MatrixConfig,
        roomId: String,
        incidents: List<IncidentRecord>,
        label: String
    ) {
        val file = withContext(Dispatchers.IO) {
            JsonExporter.writeIncidentsJson(getApplication(), incidents, label)
        }
        try {
            val body = "SL400 incidents JSON ($label)"
            matrixPublisher.sendFile(
                matrixCfg,
                roomId,
                file,
                body,
                mimeType = "application/json",
                fileName = file.name
            )
        } finally {
            runCatching { file.delete() }
        }
    }

    private fun filterJsonIncidents(
        incidents: List<IncidentRecord>,
        clipOnly: Boolean,
        metricMode: MetricMode?
    ): List<IncidentRecord> {
        var filtered = incidents
        if (clipOnly) {
            filtered = filtered.filter { it.clipPath.isNotBlank() }
        }
        if (metricMode != null) {
            filtered = filtered.filter { it.metricMode == metricMode.name }
        }
        return filtered
    }

    private fun buildJsonLabel(base: String, clipOnly: Boolean, metricMode: MetricMode?): String {
        val parts = mutableListOf(base)
        if (clipOnly) parts.add("cliponly")
        if (metricMode != null) parts.add(metricMode.name.lowercase())
        return parts.joinToString("_")
    }

    private suspend fun sendReportGraph(
        matrixCfg: MatrixConfig,
        roomId: String,
        incidents: List<IncidentRecord>,
        label: String
    ) {
        if (incidents.isEmpty()) {
            return
        }
        val primaryLabel = derivePrimaryMetricLabel(incidents)
        val showSecondary = primaryLabel != "LAeq 5 min"
        val rendered = withContext(Dispatchers.IO) {
            IncidentGraphRenderer.render(
                getApplication(),
                incidents,
                label,
                hysteresisDb = _ui.value.alertConfig.hysteresisDb,
                primaryLabel = primaryLabel,
                secondaryLabel = "LAeq 5 min",
                showSecondaryLine = showSecondary
            )
        }
        try {
            val body = "SL400 incidents graph ($label)"
            matrixPublisher.sendImage(
                matrixCfg,
                roomId,
                rendered.file,
                body,
                mimeType = "image/png",
                width = rendered.width,
                height = rendered.height,
                fileName = rendered.file.name
            )
        } finally {
            runCatching { rendered.file.delete() }
        }
    }

    private suspend fun sendReportExtras(
        matrixCfg: MatrixConfig,
        roomId: String,
        incidents: List<IncidentRecord>,
        label: String
    ) {
        val cfg = _ui.value.alertConfig
        if (cfg.dailyReportJsonEnabled) {
            sendJsonExport(matrixCfg, roomId, incidents, label)
        }
        if (cfg.dailyReportGraphEnabled) {
            sendReportGraph(matrixCfg, roomId, incidents, label)
        }
    }

    private fun buildReportLabel(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "report now (${formatter.format(Date())})"
    }

    private fun derivePrimaryMetricLabel(incidents: List<IncidentRecord>): String {
        val modes = incidents.map { it.metricMode }.distinct()
        return if (modes.size == 1) {
            IncidentFormatting.formatMetricMode(modes.first())
        } else {
            "Incident metric (mixed)"
        }
    }

    private fun buildAudioHintMessage(alertId: String, hint: AudioHintResult): String {
        return "SL400 audio hint (id=$alertId): ${AudioHintAnalyzer.formatShort(hint)}"
    }

    private fun matrixDraftFromConfig(cfg: MatrixConfig): MatrixDraft {
        return MatrixDraft(
            homeserverBaseUrl = cfg.homeserverBaseUrl,
            accessToken = cfg.accessToken,
            roomId = cfg.roomId
        )
    }

    private fun alertDraftFromConfig(cfg: AlertConfig): AlertDraft {
        return AlertDraft(
            enabled = cfg.enabled,
            thresholdText = cfg.thresholdDb.toString(),
            hysteresisText = cfg.hysteresisDb.toString(),
            intervalText = cfg.minSendIntervalMs.toString(),
            sendMode = cfg.sendMode,
            metricMode = cfg.metricMode,
            allowedSendersCsv = cfg.allowedSenders.joinToString(","),
            commandRoomId = cfg.commandRoomId,
            targetRoomId = cfg.targetRoomId,
            alertHintFollowupEnabled = cfg.alertHintFollowupEnabled,
            dailyReportEnabled = cfg.dailyReportEnabled,
            dailyReportHourText = cfg.dailyReportHour.toString(),
            dailyReportMinuteText = cfg.dailyReportMinute.toString(),
            dailyReportRoomId = cfg.dailyReportRoomId,
            dailyReportJsonEnabled = cfg.dailyReportJsonEnabled,
            dailyReportGraphEnabled = cfg.dailyReportGraphEnabled
        )
    }

    private fun startOfTodayMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfYesterdayMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DATE, -1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun metricValue(metrics: AcousticMetrics, mode: MetricMode): Double? {
        return when (mode) {
            MetricMode.LIVE -> metrics.currentDb
            MetricMode.LAEQ_1_MIN -> metrics.laEq1Min
            MetricMode.LAEQ_5_MIN -> metrics.laEq5Min
            MetricMode.LAEQ_15_MIN -> metrics.laEq15Min
            MetricMode.MAX_1_MIN -> metrics.maxDb1Min
        }
    }

    private suspend fun sendAlertIfNotSent(
        alertId: String,
        targetCfg: MatrixConfig,
        sample: Sl400Sample,
        metrics: AcousticMetrics,
        alertCfg: AlertConfig,
        audioHint: String?
    ): Boolean {
        val now = System.currentTimeMillis()
        synchronized(alertSendLock) {
            pruneSentAlertIds(now)
            if (alertId in sentAlertIds || alertId in inFlightAlertIds) return false
            inFlightAlertIds.add(alertId)
        }
        return runCatching {
            matrixPublisher.sendAlert(targetCfg, sample, metrics, alertCfg, audioHint)
        }.onSuccess {
            synchronized(alertSendLock) {
                inFlightAlertIds.remove(alertId)
                sentAlertIds[alertId] = now
            }
            _ui.update { it.copy(matrixLastStatus = "Alert sent") }
        }.onFailure { e ->
            synchronized(alertSendLock) {
                inFlightAlertIds.remove(alertId)
            }
            _ui.update { it.copy(matrixLastStatus = e.message ?: "Matrix error") }
        }.isSuccess
    }

    private fun pruneSentAlertIds(nowMs: Long) {
        val cutoff = nowMs - ALERT_SENT_RETENTION_MS
        val iterator = sentAlertIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) iterator.remove()
        }
    }

    private companion object {
        const val ALERT_HINT_TIMEOUT_MS = 25_000L
        const val ALERT_SENT_RETENTION_MS = 24L * 60L * 60L * 1000L
    }
}
