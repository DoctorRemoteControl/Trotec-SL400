package de.drremote.trotecsl400.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.drremote.trotecsl400.R
import de.drremote.trotecsl400.sl400.Sl400UiState

@Composable
fun StatusCard(ui: Sl400UiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Box {
            Image(
                painter = painterResource(id = R.drawable.sl400_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Text(
                    text = ui.lastSample?.let { "%.1f dB".format(it.db) } ?: "No measurement",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(8.dp))

                if (ui.lastSample != null) {
                    Text("aux06: ${ui.lastSample.aux06Hex ?: "-"}")
                    Text("raw: ${ui.lastSample.rawTenths}")
                }

                Spacer(Modifier.height(12.dp))
                Text(if (ui.connected) "USB connected" else "USB not connected")

                ui.matrixLastStatus?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Matrix: $it")
                }

                ui.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Error: $it")
                }
            }

            Image(
                painter = painterResource(id = R.drawable.sl400_meter),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .height(150.dp)
                    .padding(end = 16.dp, bottom = 8.dp)
                    .alpha(0.95f)
            )
        }
    }
}
