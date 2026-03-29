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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.drremote.trotecsl400.sl400.Sl400UiState

@Composable
fun MatrixCard(
    ui: Sl400UiState,
    homeserverDraft: String,
    onHomeserverChange: (String) -> Unit,
    accessTokenDraft: String,
    onAccessTokenChange: (String) -> Unit,
    roomIdDraft: String,
    onRoomIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSendTest: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Matrix", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = homeserverDraft,
                onValueChange = onHomeserverChange,
                label = { Text("Homeserver Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = accessTokenDraft,
                onValueChange = onAccessTokenChange,
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = roomIdDraft,
                onValueChange = onRoomIdChange,
                label = { Text("Room ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Matrix sending enabled")
                Switch(
                    checked = ui.matrixConfig.enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
                Button(onClick = onSendTest, modifier = Modifier.weight(1f)) { Text("Send test") }
            }
        }
    }
}
