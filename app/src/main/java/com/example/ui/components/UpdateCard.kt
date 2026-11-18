package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.updates.UpdateStatus
import com.example.updates.UpdateUiState
import java.util.Locale

/** Self-update entry point shown at the bottom of Ajustes. */
@Composable
fun UpdateCard(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Debug builds are signed with a different key and could never update in place.
    if (!state.isReleaseBuild) return
    val info = state.info

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            when {
                state.status == UpdateStatus.CHECKING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Buscando actualizaciones…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                info != null && state.status == UpdateStatus.READY -> {
                    Text(
                        text = "Actualización descargada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Versión ${info.versionName} verificada. Android te pedirá confirmar la instalación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Text("Instalar")
                    }
                    TextButton(onClick = onOpenRelease) { Text("Ver cambios") }
                }

                info != null && state.status == UpdateStatus.DOWNLOADING -> {
                    Text(
                        text = "Descargando versión ${info.versionName}…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${state.progress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                info != null -> {
                    Text(
                        text = "Actualización disponible",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val size = formatSize(info.sizeBytes)
                    Text(
                        text = listOfNotNull("Versión ${info.versionName}", size.ifBlank { null })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val summary = info.notes.lineSequence().firstOrNull { it.isNotBlank() }
                    if (!summary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = summary.take(160),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Descargar")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Ahora no") }
                        TextButton(onClick = onOpenRelease) { Text("Ver cambios") }
                    }
                }

                else -> {
                    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("Buscar actualizaciones")
                    }
                    state.message?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0L) "" else String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
