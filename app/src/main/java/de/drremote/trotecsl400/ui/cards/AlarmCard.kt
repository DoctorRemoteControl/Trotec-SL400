package de.drremote.trotecsl400.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.drremote.trotecsl400.alert.MetricMode
import de.drremote.trotecsl400.alert.SendMode

@Composable
fun AlarmCard(
    alertEnabledDraft: Boolean,
    onAlertEnabledChange: (Boolean) -> Unit,
    alertThresholdDraft: String,
    onThresholdChange: (String) -> Unit,
    alertHysteresisDraft: String,
    onHysteresisChange: (String) -> Unit,
    alertIntervalDraft: String,
    onIntervalChange: (String) -> Unit,
    alertModeDraft: SendMode,
    onModeChange: (SendMode) -> Unit,
    alertMetricModeDraft: MetricMode,
    onMetricModeChange: (MetricMode) -> Unit,
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
    onSave: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Alert Rules", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Alerts enabled")
                Switch(
                    checked = alertEnabledDraft,
                    onCheckedChange = onAlertEnabledChange
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = alertThresholdDraft,
                onValueChange = onThresholdChange,
                label = { Text("Threshold dB") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = alertHysteresisDraft,
                onValueChange = onHysteresisChange,
                label = { Text("Hysteresis dB") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = alertIntervalDraft,
                onValueChange = onIntervalChange,
                label = { Text("Min interval ms") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text("Mode")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onModeChange(SendMode.CROSSING_ONLY) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Crossing")
                }
                Button(
                    onClick = { onModeChange(SendMode.PERIODIC_WHILE_ABOVE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Periodic")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Current mode: " +
                    if (alertModeDraft == SendMode.CROSSING_ONLY) "Crossing" else "Periodic"
            )

            Spacer(Modifier.height(12.dp))

            Text("Metric")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onMetricModeChange(MetricMode.LIVE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Live")
                }
                Button(
                    onClick = { onMetricModeChange(MetricMode.LAEQ_1_MIN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("LAeq 1m")
                }
                Button(
                    onClick = { onMetricModeChange(MetricMode.LAEQ_5_MIN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("LAeq 5m")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onMetricModeChange(MetricMode.LAEQ_15_MIN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("LAeq 15m")
                }
                Button(
                    onClick = { onMetricModeChange(MetricMode.MAX_1_MIN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Max 1m")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Current metric: ${metricLabel(alertMetricModeDraft)}")

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = alertCommandRoomDraft,
                onValueChange = onCommandRoomChange,
                label = { Text("Command Room ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = alertTargetRoomDraft,
                onValueChange = onTargetRoomChange,
                label = { Text("Target Room ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = alertAllowedSendersDraft,
                onValueChange = onAllowedSendersChange,
                label = { Text("Allowed senders CSV") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Send audio hint follow-up")
                Switch(
                    checked = alertHintFollowupEnabledDraft,
                    onCheckedChange = onAlertHintFollowupEnabledChange
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            Text("Daily Report", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enabled")
                Switch(
                    checked = dailyReportEnabledDraft,
                    onCheckedChange = onDailyReportEnabledChange
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dailyReportHourDraft,
                    onValueChange = onDailyReportHourChange,
                    label = { Text("Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dailyReportMinuteDraft,
                    onValueChange = onDailyReportMinuteChange,
                    label = { Text("Minute (0-59)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dailyReportRoomDraft,
                onValueChange = onDailyReportRoomChange,
                label = { Text("Report Room ID (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Attach JSON")
                Switch(
                    checked = dailyReportJsonEnabledDraft,
                    onCheckedChange = onDailyReportJsonEnabledChange
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Attach Graph")
                Switch(
                    checked = dailyReportGraphEnabledDraft,
                    onCheckedChange = onDailyReportGraphEnabledChange
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save alerts")
            }
        }
    }
}

private fun metricLabel(mode: MetricMode): String {
    return when (mode) {
        MetricMode.LIVE -> "Live"
        MetricMode.LAEQ_1_MIN -> "LAeq 1 min"
        MetricMode.LAEQ_5_MIN -> "LAeq 5 min"
        MetricMode.LAEQ_15_MIN -> "LAeq 15 min"
        MetricMode.MAX_1_MIN -> "Max 1 min"
    }
}
