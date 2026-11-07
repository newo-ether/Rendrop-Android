package com.newoether.rendrop

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.rendrop.ui.theme.MonoLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FieldShape = RoundedCornerShape(16.dp)

/** Which device dialog is open and its draft values; survives configuration changes. */
class DeviceDialogState {
    var showScan by mutableStateOf(false)
    var showForm by mutableStateOf(false)
    var showDelete by mutableStateOf(false)
    /** IP being edited; empty while adding. */
    var originalIp by mutableStateOf("")
    var ip by mutableStateOf("")
    var name by mutableStateOf("")

    fun openAdd() {
        originalIp = ""; ip = ""; name = ""; showForm = true
    }

    fun openEdit(deviceIp: String, deviceName: String) {
        originalIp = deviceIp; ip = deviceIp; name = deviceName; showForm = true
    }

    fun openDelete(device: Pair<String, String>) {
        ip = device.first; name = device.second; showDelete = true
    }

    companion object {
        val Saver = listSaver<DeviceDialogState, Any>(
            save = { listOf(it.showScan, it.showForm, it.showDelete, it.originalIp, it.ip, it.name) },
            restore = {
                DeviceDialogState().apply {
                    showScan = it[0] as Boolean
                    showForm = it[1] as Boolean
                    showDelete = it[2] as Boolean
                    originalIp = it[3] as String
                    ip = it[4] as String
                    name = it[5] as String
                }
            }
        )
    }
}

@Composable
fun rememberDeviceDialogState(): DeviceDialogState =
    rememberSaveable(saver = DeviceDialogState.Saver) { DeviceDialogState() }

@Composable
fun DeviceDialogs(
    state: DeviceDialogState,
    devices: List<Pair<String, String>>,
    onDevicesChange: (List<Pair<String, String>>) -> Unit,
) {
    if (state.showScan) {
        ScanDeviceDialog(
            onDismiss = { state.showScan = false },
            devices = devices,
            onDevicesChange = onDevicesChange
        )
    }
    if (state.showForm) {
        DeviceFormDialog(state = state, devices = devices, onDevicesChange = onDevicesChange)
    }
    if (state.showDelete) {
        AlertDialog(
            onDismissRequest = { state.showDelete = false },
            title = { Text(stringResource(R.string.confirm_delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_device_msg, state.name, state.ip)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ip = state.ip
                        onDevicesChange(devices.filter { it.first != ip })
                        state.showDelete = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { state.showDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Shared add/edit form. Adding when [DeviceDialogState.originalIp] is empty. */
@Composable
private fun DeviceFormDialog(
    state: DeviceDialogState,
    devices: List<Pair<String, String>>,
    onDevicesChange: (List<Pair<String, String>>) -> Unit,
) {
    val editing = state.originalIp.isNotEmpty()
    val ipValid = isValidIp(state.ip)
    val duplicated = devices.any { it.first == state.ip && it.first != state.originalIp }
    val ipError = when {
        state.ip.isNotEmpty() && !ipValid -> stringResource(R.string.invalid_ip)
        duplicated -> stringResource(R.string.ip_exists)
        else -> null
    }
    AlertDialog(
        onDismissRequest = { state.showForm = false },
        title = { Text(stringResource(if (editing) R.string.edit_device else R.string.add_device), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.ip,
                    onValueChange = { state.ip = it.trim() },
                    label = { Text(stringResource(R.string.ip_address)) },
                    singleLine = true,
                    isError = ipError != null,
                    supportingText = ipError?.let { { Text(it) } },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoLabel.fontFamily),
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    label = { Text(stringResource(R.string.device_name)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val entry = state.ip to state.name
                    onDevicesChange(
                        if (editing) devices.map { if (it.first == state.originalIp) entry else it }
                        else devices + entry
                    )
                    state.showForm = false
                },
                enabled = ipValid && state.name.isNotBlank() && !duplicated
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { state.showForm = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private data class ScanItem(val ip: String, val name: String, val checked: Boolean, val enabled: Boolean)

@Composable
private fun ScanDeviceDialog(
    onDismiss: () -> Unit,
    devices: List<Pair<String, String>>,
    onDevicesChange: (List<Pair<String, String>>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Not saveable: a scan interrupted by recreation must be restartable.
    var loading by remember { mutableStateOf(false) }
    val found = rememberSaveable(
        saver = listSaver(
            save = { list -> list.map { listOf(it.ip, it.name, it.checked, it.enabled) } },
            restore = { saved ->
                saved.map {
                    ScanItem(it[0] as String, it[1] as String, it[2] as Boolean, it[3] as Boolean)
                }.toMutableStateList()
            }
        )
    ) { mutableStateListOf<ScanItem>() }

    suspend fun refresh() {
        if (loading) return
        loading = true
        val result = withContext(Dispatchers.IO) { scanLanDevices(context) }
        val previouslyChecked = found.filter { it.checked }.map { it.ip }.toSet()
        val next = result.map { (ip, name) ->
            val known = devices.any { it.first == ip }
            ScanItem(ip, name, checked = known || ip in previouslyChecked, enabled = !known)
        }
        found.clear()
        found.addAll(next)
        loading = false
    }

    LaunchedEffect(Unit) { if (found.isEmpty()) refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.select_device), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    androidx.compose.animation.AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)) {
                    if (found.isEmpty()) {
                        Text(
                            stringResource(R.string.no_devices_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            found.forEachIndexed { index, item ->
                                ScanRow(item) { found[index] = item.copy(checked = !item.checked) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val added = found.filter { it.checked && it.enabled }.map { it.ip to it.name }
                onDevicesChange(devices + added)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ScanRow(item: ScanItem, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.checked, enabled = item.enabled, onCheckedChange = { onToggle() })
        Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(item.ip, style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
