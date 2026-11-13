package com.example.ui.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ussd.SimInfoProvider

/**
 * Per-line SIM picker. The null value is explicit: "use the system default
 * subscription", which is useful on single-SIM devices or when the user has
 * not chosen a physical SIM yet.
 */
@Composable
fun SimSelector(
    selectedSlotIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var sims by remember { mutableStateOf(SimInfoProvider.activeSims(context)) }
    var hasPermission by remember { mutableStateOf(SimInfoProvider.hasPermission(context)) }
    var expanded by remember { mutableStateOf(false) }

    fun refresh() {
        hasPermission = SimInfoProvider.hasPermission(context)
        sims = SimInfoProvider.activeSims(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        sims = SimInfoProvider.activeSims(context)
        if (!granted) {
            Toast.makeText(
                context,
                "Sin este permiso no se pueden listar las SIM; se usará la predeterminada.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selected = sims.firstOrNull { it.slotIndex == selectedSlotIndex }
    val selectedLabel = when {
        selected != null -> selected.label
        selectedSlotIndex != null -> "SIM ${selectedSlotIndex + 1} (no disponible)"
        else -> "Automática (SIM predeterminada)"
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "SIM de esta línea",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        when {
            !hasPermission -> {
                Text(
                    text = "Permite el acceso al teléfono para elegir cuál SIM consulta el saldo de esta línea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Permitir acceso a las SIM", style = MaterialTheme.typography.labelSmall)
                }
            }

            sims.isEmpty() -> {
                Text(
                    text = "No se detectaron SIM activas. Se usará la SIM predeterminada del sistema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedLabel,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Elegir SIM",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Automática (SIM predeterminada)") },
                            onClick = {
                                onSelect(null)
                                expanded = false
                            }
                        )
                        sims.forEach { sim ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        sim.label + if (sim.isDefault) " · predeterminada" else ""
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    onSelect(sim.slotIndex)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = when {
                        selectedSlotIndex != null && selected == null ->
                            "La SIM guardada ya no está activa. Elige otra SIM."

                        selected != null ->
                            "El saldo de esta línea se consultará en ${selected.label}."

                        sims.size > 1 ->
                            "Hay ${sims.size} SIM activas. Elige la de esta línea para no consultar la SIM equivocada."

                        else ->
                            "Se usará la SIM predeterminada del sistema."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedSlotIndex != null && selected == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
