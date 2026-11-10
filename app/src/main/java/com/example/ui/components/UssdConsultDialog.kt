package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.UssdParser
import com.example.ui.theme.StatusSuccess

@Composable
fun UssdConsultDialog(
    currentUssdCode: String,
    onDismiss: () -> Unit,
    onSaveUssdCode: (String) -> Unit,
    onApplyDetectedMB: (Double) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var ussdCode by remember { mutableStateOf(currentUssdCode) }
    var responseText by remember { mutableStateOf("") }
    var detectedResult by remember { mutableStateOf<String?>(null) }
    var detectedMB by remember { mutableStateOf<Double?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Consulta de Saldo por USSD", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                // Policy & Technical Note Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Por privacidad y políticas de Google Play, Android no permite a apps de terceros interceptar llamadas o leer la ventana USSD del sistema sin confirmación del usuario. Aquí puedes marcar tu código en 1 toque y pegar la respuesta para detectarlo automáticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 1: Dial USSD Code
                Text(
                    text = "1. Marcar código USSD de tu operadora",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ussdCode,
                        onValueChange = {
                            ussdCode = it
                            onSaveUssdCode(it)
                        },
                        label = { Text("Código USSD") },
                        placeholder = { Text("*222# o *133#") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ussd_code_input")
                    )

                    Button(
                        onClick = {
                            try {
                                val encoded = Uri.encode(ussdCode.trim())
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encoded"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("dial_ussd_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Llamar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Paste & Detect Response
                Text(
                    text = "2. Pega el texto o SMS recibido",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = responseText,
                    onValueChange = {
                        responseText = it
                        val mb = UssdParser.parseDataBalanceMB(it)
                        if (mb != null) {
                            detectedMB = mb
                            detectedResult = "Detectado: ${DataFormatters.format(mb, "auto")}"
                        } else {
                            detectedMB = null
                            detectedResult = null
                        }
                    },
                    label = { Text("Respuesta del operador / SMS") },
                    placeholder = { Text("Ej. Tu saldo de datos es 4.2 GB...") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ussd_response_text_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                responseText = clip
                                val mb = UssdParser.parseDataBalanceMB(clip)
                                if (mb != null) {
                                    detectedMB = mb
                                    detectedResult = "Detectado: ${DataFormatters.format(mb, "auto")}"
                                } else {
                                    Toast.makeText(context, "No se detectó una cantidad de datos en el portapapeles", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pegar", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = {
                            val mb = UssdParser.parseDataBalanceMB(responseText)
                            if (mb != null) {
                                detectedMB = mb
                                detectedResult = "Detectado: ${DataFormatters.format(mb, "auto")}"
                            } else {
                                Toast.makeText(context, "No se encontraron MB o GB en el texto", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Analizar", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (detectedResult != null && detectedMB != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusSuccess.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = detectedResult!!,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusSuccess
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (detectedMB != null) {
                        onApplyDetectedMB(detectedMB!!)
                        onDismiss()
                    }
                },
                enabled = detectedMB != null,
                modifier = Modifier.testTag("apply_detected_balance_button")
            ) {
                Text("Aplicar Saldo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
