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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AudioTestCard(
    isRecording: Boolean,
    statusText: String,
    deviceInfo: String,
    sampleRate: String,
    channels: String,
    filePath: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("USB Audio Test", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    enabled = !isRecording
                ) {
                    Text("Test USB Mic")
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    enabled = isRecording
                ) {
                    Text("Stop Test Recording")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Status: $statusText")
            Text("Device: $deviceInfo")
            Text("Sample Rate: $sampleRate")
            Text("Channels: $channels")
            if (filePath.isNotBlank()) {
                Text("File: $filePath")
            }
        }
    }
}
