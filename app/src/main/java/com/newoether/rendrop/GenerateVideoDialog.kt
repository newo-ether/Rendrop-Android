package com.newoether.rendrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.rendrop.ui.components.RendropDropdownMenu
import com.newoether.rendrop.ui.components.RendropDropdownMenuItem

private val FieldShape = RoundedCornerShape(16.dp)

@Composable
fun GenerateVideoDialog(
    onDismiss: () -> Unit,
    onGenerate: (String, Int) -> Unit
) {
    var quality by remember { mutableStateOf("low") }
    var fpsText by remember { mutableStateOf("30") }
    var expanded by remember { mutableStateOf(false) }
    val qualities = listOf("low" to R.string.low_quality, "high" to R.string.high_quality)
    val fps = fpsText.toIntOrNull()?.takeIf { it in 1..240 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generate_video), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    // Read-only field acting as the anchor for the quality menu.
                    OutlinedTextField(
                        value = stringResource(qualities.first { it.first == quality }.second),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.quality)) },
                        trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Transparent overlay so the whole field opens the menu.
                    TextButton(
                        onClick = { expanded = true },
                        shape = FieldShape,
                        modifier = Modifier.matchParentSize()
                    ) {}
                    RendropDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        qualities.forEach { (key, label) ->
                            RendropDropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                leadingIcon = {
                                    if (quality == key) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = { quality = key; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = fpsText,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) fpsText = it },
                    label = { Text(stringResource(R.string.frame_rate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = fpsText.isNotEmpty() && fps == null,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { fps?.let { onGenerate(quality, it) } }, enabled = fps != null) {
                Text(stringResource(R.string.generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
