package de.drremote.trotecsl400

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.drremote.trotecsl400.audio.AudioTestRecorder
import de.drremote.trotecsl400.sl400.Sl400ViewModel
import de.drremote.trotecsl400.ui.theme.TrotecSL400Theme
import de.drremote.trotecsl400.ui.screens.HomeScreen
import de.drremote.trotecsl400.ui.screens.SettingsScreen
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {

    private val vm by viewModels<Sl400ViewModel>()
    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }
    private val audioPermissionRequestCode = 2001

    private val permissionAction = "de.drremote.trotecsl400.USB_PERMISSION"
    private var receiversRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (intent.action != permissionAction) return
                val device = intent.getUsbDeviceExtra()
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                Log.i("SL400", "permissionReceiver granted=$granted device=$device")

                if (granted && device != null) {
                    vm.connect(device)
                } else {
                    vm.onPermissionDenied()
                }
            } catch (t: Throwable) {
                Log.e("SL400", "permissionReceiver crashed", t)
            }
        }
    }

    private val usbDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        Log.i("SL400", "USB attached")
                        vm.refreshDevices()
                    }

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = intent.getUsbDeviceExtra()
                        Log.i("SL400", "USB detached device=$device")
                        if (device != null) {
                            vm.onDeviceDetached(device)
                        }
                        vm.refreshDevices()
                    }
                }
            } catch (t: Throwable) {
                Log.e("SL400", "usbDeviceReceiver crashed", t)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            var showSettings by rememberSaveable { mutableStateOf(false) }

            val matrixDraft = ui.matrixDraft
            val alertDraft = ui.alertDraft

            val audioRecorder = remember { AudioTestRecorder(applicationContext) }
            var audioStatus by rememberSaveable { mutableStateOf("Idle") }
            var audioDeviceInfo by rememberSaveable { mutableStateOf("None") }
            var audioSampleRate by rememberSaveable { mutableStateOf("0") }
            var audioChannels by rememberSaveable { mutableStateOf("0") }
            var audioFilePath by rememberSaveable { mutableStateOf("") }
            var audioIsRecording by rememberSaveable { mutableStateOf(false) }

            TrotecSL400Theme {
                @OptIn(ExperimentalMaterial3Api::class)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text("SL400 Reader") }) }
                ) { innerPadding ->
                    if (!showSettings) {
                        HomeScreen(
                            ui = ui,
                            modifier = Modifier.padding(innerPadding),
                            onOpenSettings = { showSettings = true },
                            onAutoConnect = {
                                val device = ui.devices.firstOrNull()
                                if (device == null) {
                                    vm.refreshDevices()
                                } else {
                                    Log.i("SL400", "auto-connect ${device.displayName()}")
                                    if (vm.hasPermission(device)) {
                                        vm.connect(device)
                                    } else {
                                        requestUsbPermission(device)
                                    }
                                }
                            }
                        )
                    } else {
                        SettingsScreen(
                            ui = ui,
                            onBack = { showSettings = false },
                            onRefresh = { vm.refreshDevices() },
                            onDisconnect = { vm.disconnect() },
                            onConnectDevice = { device ->
                                Log.i("SL400", "connect click ${device.displayName()}")
                                if (vm.hasPermission(device)) {
                                    vm.connect(device)
                                } else {
                                    requestUsbPermission(device)
                                }
                            },
                            homeserverDraft = matrixDraft.homeserverBaseUrl,
                            onHomeserverChange = { value ->
                                vm.updateMatrixDraft { draft -> draft.copy(homeserverBaseUrl = value) }
                            },
                            accessTokenDraft = matrixDraft.accessToken,
                            onAccessTokenChange = { value ->
                                vm.updateMatrixDraft { draft -> draft.copy(accessToken = value) }
                            },
                            roomIdDraft = matrixDraft.roomId,
                            onRoomIdChange = { value ->
                                vm.updateMatrixDraft { draft -> draft.copy(roomId = value) }
                            },
                            onSaveMatrix = { vm.saveMatrixDraft() },
                            onMatrixEnabledChange = { vm.setMatrixEnabled(it) },
                            onSendTest = { vm.sendMatrixTestMessage() },
                            alertEnabledDraft = alertDraft.enabled,
                            onAlertEnabledChange = { value ->
                                vm.updateAlertDraft { it.copy(enabled = value) }
                            },
                            alertThresholdDraft = alertDraft.thresholdText,
                            onThresholdChange = { value ->
                                vm.updateAlertDraft { it.copy(thresholdText = value) }
                            },
                            alertHysteresisDraft = alertDraft.hysteresisText,
                            onHysteresisChange = { value ->
                                vm.updateAlertDraft { it.copy(hysteresisText = value) }
                            },
                            alertIntervalDraft = alertDraft.intervalText,
                            onIntervalChange = { value ->
                                vm.updateAlertDraft { it.copy(intervalText = value) }
                            },
                            alertModeDraft = alertDraft.sendMode,
                            onModeChange = { value ->
                                vm.updateAlertDraft { it.copy(sendMode = value) }
                            },
                            alertMetricModeDraft = alertDraft.metricMode,
                            onMetricModeChange = { value ->
                                vm.updateAlertDraft { it.copy(metricMode = value) }
                            },
                            alertCommandRoomDraft = alertDraft.commandRoomId,
                            onCommandRoomChange = { value ->
                                vm.updateAlertDraft { it.copy(commandRoomId = value) }
                            },
                            alertTargetRoomDraft = alertDraft.targetRoomId,
                            onTargetRoomChange = { value ->
                                vm.updateAlertDraft { it.copy(targetRoomId = value) }
                            },
                            alertHintFollowupEnabledDraft = alertDraft.alertHintFollowupEnabled,
                            onAlertHintFollowupEnabledChange = { value ->
                                vm.updateAlertDraft { it.copy(alertHintFollowupEnabled = value) }
                            },
                            dailyReportEnabledDraft = alertDraft.dailyReportEnabled,
                            onDailyReportEnabledChange = { value ->
                                vm.updateAlertDraft { it.copy(dailyReportEnabled = value) }
                            },
                            dailyReportHourDraft = alertDraft.dailyReportHourText,
                            onDailyReportHourChange = { value ->
                                vm.updateAlertDraft { it.copy(dailyReportHourText = value) }
                            },
                            dailyReportMinuteDraft = alertDraft.dailyReportMinuteText,
                            onDailyReportMinuteChange = { value ->
                                vm.updateAlertDraft { it.copy(dailyReportMinuteText = value) }
                            },
                            dailyReportRoomDraft = alertDraft.dailyReportRoomId,
                            onDailyReportRoomChange = { value ->
                                vm.updateAlertDraft { it.copy(dailyReportRoomId = value) }
                            },
                            dailyReportJsonEnabledDraft = alertDraft.dailyReportJsonEnabled,
                            onDailyReportJsonEnabledChange = { value ->
                                vm.updateAlertDraft { it.copy(dailyReportJsonEnabled = value) }
                            },
                            dailyReportGraphEnabledDraft = alertDraft.dailyReportGraphEnabled,
                            onDailyReportGraphEnabledChange = { value ->
                                vm.updateAlertDraft { it.copy(dailyReportGraphEnabled = value) }
                            },
                            alertAllowedSendersDraft = alertDraft.allowedSendersCsv,
                            onAllowedSendersChange = { value ->
                                vm.updateAlertDraft { it.copy(allowedSendersCsv = value) }
                            },
                            onSaveAlerts = { vm.saveAlertDraft() },
                            audioIsRecording = audioIsRecording,
                            audioStatus = audioStatus,
                            audioDeviceInfo = audioDeviceInfo,
                            audioSampleRate = audioSampleRate,
                            audioChannels = audioChannels,
                            audioFilePath = audioFilePath,
                            onAudioStart = {
                                if (!ensureAudioPermission()) {
                                    audioStatus = "Microphone permission required"
                                    return@SettingsScreen
                                }
                                audioRecorder.startTestRecording(10_000) { status ->
                                    audioStatus = status.message
                                    audioDeviceInfo = status.deviceInfo
                                    audioSampleRate = if (status.sampleRate > 0) {
                                        "${status.sampleRate} Hz"
                                    } else {
                                        "0"
                                    }
                                    audioChannels = if (status.channels > 0) {
                                        "${status.channels}"
                                    } else {
                                        "0"
                                    }
                                    if (status.filePath.isNotBlank()) {
                                        audioFilePath = status.filePath
                                    }
                                    audioIsRecording = audioRecorder.isRecording()
                                }
                            },
                            onAudioStop = {
                                audioRecorder.stop { status ->
                                    audioStatus = status.message
                                    if (status.filePath.isNotBlank()) {
                                        audioFilePath = status.filePath
                                    }
                                    audioIsRecording = audioRecorder.isRecording()
                                }
                            },
                            alarmAudioRunning = ui.audioCaptureRunning,
                            alarmAudioStatus = ui.audioCaptureStatus,
                            alarmAudioDevice = ui.audioCaptureDevice,
                            alarmAudioSampleRate = ui.audioCaptureSampleRate,
                            alarmAudioBufferSeconds = ui.audioCaptureBufferSeconds,
                            alarmAudioLastClipPath = ui.audioCaptureLastClipPath,
                            onAlarmAudioStart = {
                                if (!ensureAudioPermission()) {
                                    return@SettingsScreen
                                }
                                vm.startAudioCapture()
                            },
                            onAlarmAudioStop = { vm.stopAudioCapture() },
                            samples = ui.samples.take(8)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestNotificationPermissionIfNeeded()
        if (!receiversRegistered) {
            registerUsbReceivers()
            receiversRegistered = true
        }
        vm.refreshDevices()
    }

    override fun onStop() {
        if (receiversRegistered) {
            try {
                unregisterReceiver(permissionReceiver)
                unregisterReceiver(usbDeviceReceiver)
            } catch (t: Throwable) {
                Log.w("SL400", "unregisterReceiver failed", t)
            }
            receiversRegistered = false
        }
        super.onStop()
    }

    private fun registerUsbReceivers() {
        val permissionFilter = IntentFilter(permissionAction)
        val deviceFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(permissionReceiver, permissionFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(usbDeviceReceiver, deviceFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(permissionReceiver, permissionFilter)
            registerReceiver(usbDeviceReceiver, deviceFilter)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    private fun ensureAudioPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                audioPermissionRequestCode
            )
        }
        return granted
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val intent = PendingIntent.getBroadcast(
            this,
            device.deviceId,
            Intent(permissionAction).setPackage(packageName),
            flags
        )
        usbManager.requestPermission(device, intent)
    }

private fun Intent.getUsbDeviceExtra(): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

}
