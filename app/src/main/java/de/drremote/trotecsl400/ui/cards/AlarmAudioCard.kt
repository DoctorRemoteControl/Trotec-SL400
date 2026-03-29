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
fun AlarmAudioCard(
    isRunning: Boolean,
    statusText: String,
    deviceInfo: String,
    sampleRate: Int,
    bufferSeconds: Int,
    lastClipPath: String,
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
            Text("Alarm Audio Buffer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning
                ) {
                    Text("Start Buffer")
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    enabled = isRunning
                ) {
                    Text("Stop Buffer")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Status: $statusText")
            Text("Device: $deviceInfo")
            Text("Sample Rate: ${if (sampleRate > 0) "$sampleRate Hz" else "0"}")
            Text("Buffer: ${if (bufferSeconds > 0) "$bufferSeconds s" else "0"}")
            if (lastClipPath.isNotBlank()) {
                Text("Last clip: $lastClipPath")
            }
        }
    }
}
