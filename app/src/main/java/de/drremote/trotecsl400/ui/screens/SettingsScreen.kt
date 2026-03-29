package de.drremote.trotecsl400.ui.screens

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.drremote.trotecsl400.sl400.Sl400Sample
import de.drremote.trotecsl400.sl400.Sl400UiState
import de.drremote.trotecsl400.ui.cards.AlarmAudioCard
import de.drremote.trotecsl400.ui.cards.AlarmCard
import de.drremote.trotecsl400.ui.cards.AudioTestCard
import de.drremote.trotecsl400.ui.cards.ConnectionCard
import de.drremote.trotecsl400.ui.cards.MatrixCard
import de.drremote.trotecsl400.ui.cards.RecentSamplesCard

@Composable
fun SettingsScreen(
    ui: Sl400UiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onConnectDevice: (UsbDevice) -> Unit,
    homeserverDraft: String,
    onHomeserverChange: (String) -> Unit,
    accessTokenDraft: String,
    onAccessTokenChange: (String) -> Unit,
    roomIdDraft: String,
    onRoomIdChange: (String) -> Unit,
    onSaveMatrix: () -> Unit,
    onMatrixEnabledChange: (Boolean) -> Unit,
    onSendTest: () -> Unit,
    alertEnabledDraft: Boolean,
    onAlertEnabledChange: (Boolean) -> Unit,
    alertThresholdDraft: String,
    onThresholdChange: (String) -> Unit,
    alertHysteresisDraft: String,
    onHysteresisChange: (String) -> Unit,
    alertIntervalDraft: String,
    onIntervalChange: (String) -> Unit,
    alertModeDraft: de.drremote.trotecsl400.alert.SendMode,
    onModeChange: (de.drremote.trotecsl400.alert.SendMode) -> Unit,
    alertMetricModeDraft: de.drremote.trotecsl400.alert.MetricMode,
    onMetricModeChange: (de.drremote.trotecsl400.alert.MetricMode) -> Unit,
    alertCommandRoomDraft: String,
    onCommandRoomChange: (String) -> Unit,
    alertTargetRoomDraft: String,
    onTargetRoomChange: (String) -> Unit,
    alertHintFollowupEnabledDraft: Boolean,
    onAlertHintFollowupEnabledChange: (Boolean) -> Unit,
    dailyReportEnabledDraft: Boolean,
    onDailyReportEnabledChange: (Boolean) -> Unit,
    dailyReportHourDraft: String,
    onDailyReportHourChange: (String) -> Unit,
    dailyReportMinuteDraft: String,
    onDailyReportMinuteChange: (String) -> Unit,
    dailyReportRoomDraft: String,
    onDailyReportRoomChange: (String) -> Unit,
    dailyReportJsonEnabledDraft: Boolean,
    onDailyReportJsonEnabledChange: (Boolean) -> Unit,
    dailyReportGraphEnabledDraft: Boolean,
    onDailyReportGraphEnabledChange: (Boolean) -> Unit,
    alertAllowedSendersDraft: String,
    onAllowedSendersChange: (String) -> Unit,
    onSaveAlerts: () -> Unit,
    audioIsRecording: Boolean,
    audioStatus: String,
    audioDeviceInfo: String,
    audioSampleRate: String,
    audioChannels: String,
    audioFilePath: String,
    onAudioStart: () -> Unit,
    onAudioStop: () -> Unit,
    alarmAudioRunning: Boolean,
    alarmAudioStatus: String,
    alarmAudioDevice: String,
    alarmAudioSampleRate: Int,
    alarmAudioBufferSeconds: Int,
    alarmAudioLastClipPath: String,
    onAlarmAudioStart: () -> Unit,
    onAlarmAudioStop: () -> Unit,
    samples: List<Sl400Sample>
) {
    LazyColumn(
        modifier = Modifier
            .padding(0.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsHeader(onBack = onBack)
        }

        item {
            ConnectionCard(
                ui = ui,
                onRefresh = onRefresh,
                onDisconnect = onDisconnect,
                onConnectDevice = onConnectDevice
            )
        }

        item {
            MatrixCard(
                ui = ui,
                homeserverDraft = homeserverDraft,
                onHomeserverChange = onHomeserverChange,
                accessTokenDraft = accessTokenDraft,
                onAccessTokenChange = onAccessTokenChange,
                roomIdDraft = roomIdDraft,
                onRoomIdChange = onRoomIdChange,
                onSave = onSaveMatrix,
                onEnabledChange = onMatrixEnabledChange,
                onSendTest = onSendTest
            )
        }

        item {
            AlarmCard(
                alertEnabledDraft = alertEnabledDraft,
                onAlertEnabledChange = onAlertEnabledChange,
                alertThresholdDraft = alertThresholdDraft,
                onThresholdChange = onThresholdChange,
                alertHysteresisDraft = alertHysteresisDraft,
                onHysteresisChange = onHysteresisChange,
                alertIntervalDraft = alertIntervalDraft,
                onIntervalChange = onIntervalChange,
                alertModeDraft = alertModeDraft,
                onModeChange = onModeChange,
                alertMetricModeDraft = alertMetricModeDraft,
                onMetricModeChange = onMetricModeChange,
                alertCommandRoomDraft = alertCommandRoomDraft,
                onCommandRoomChange = onCommandRoomChange,
                alertTargetRoomDraft = alertTargetRoomDraft,
                onTargetRoomChange = onTargetRoomChange,
                alertHintFollowupEnabledDraft = alertHintFollowupEnabledDraft,
                onAlertHintFollowupEnabledChange = onAlertHintFollowupEnabledChange,
                dailyReportEnabledDraft = dailyReportEnabledDraft,
                onDailyReportEnabledChange = onDailyReportEnabledChange,
                dailyReportHourDraft = dailyReportHourDraft,
                onDailyReportHourChange = onDailyReportHourChange,
                dailyReportMinuteDraft = dailyReportMinuteDraft,
                onDailyReportMinuteChange = onDailyReportMinuteChange,
                dailyReportRoomDraft = dailyReportRoomDraft,
                onDailyReportRoomChange = onDailyReportRoomChange,
                dailyReportJsonEnabledDraft = dailyReportJsonEnabledDraft,
                onDailyReportJsonEnabledChange = onDailyReportJsonEnabledChange,
                dailyReportGraphEnabledDraft = dailyReportGraphEnabledDraft,
                onDailyReportGraphEnabledChange = onDailyReportGraphEnabledChange,
                alertAllowedSendersDraft = alertAllowedSendersDraft,
                onAllowedSendersChange = onAllowedSendersChange,
                onSave = onSaveAlerts
            )
        }

        item {
            AudioTestCard(
                isRecording = audioIsRecording,
                statusText = audioStatus,
                deviceInfo = audioDeviceInfo,
                sampleRate = audioSampleRate,
                channels = audioChannels,
                filePath = audioFilePath,
                onStart = onAudioStart,
                onStop = onAudioStop
            )
        }

        item {
            AlarmAudioCard(
                isRunning = alarmAudioRunning,
                statusText = alarmAudioStatus,
                deviceInfo = alarmAudioDevice,
                sampleRate = alarmAudioSampleRate,
                bufferSeconds = alarmAudioBufferSeconds,
                lastClipPath = alarmAudioLastClipPath,
                onStart = onAlarmAudioStart,
                onStop = onAlarmAudioStop
            )
        }

        item {
            RecentSamplesCard(samples = samples)
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
