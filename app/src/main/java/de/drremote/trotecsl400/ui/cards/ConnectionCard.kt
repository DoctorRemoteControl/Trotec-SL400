package de.drremote.trotecsl400.ui.cards

import android.hardware.usb.UsbDevice
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
import de.drremote.trotecsl400.sl400.Sl400UiState
import de.drremote.trotecsl400.displayName

@Composable
fun ConnectionCard(
    ui: Sl400UiState,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onConnectDevice: (UsbDevice) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh) { Text("Scan devices") }
                if (ui.connected) {
                    Button(onClick = onDisconnect) { Text("Disconnect") }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (ui.devices.isEmpty()) {
                Text("No compatible USB serial devices found.")
            } else {
                ui.devices.forEach { device ->
                    Button(
                        onClick = { onConnectDevice(device) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ui.connectedDeviceId != device.deviceId
                    ) {
                        Text(device.displayName())
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
