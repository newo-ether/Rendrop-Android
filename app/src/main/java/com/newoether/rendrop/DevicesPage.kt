package com.newoether.rendrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.newoether.rendrop.ui.components.CollapsingTitleBar
import com.newoether.rendrop.ui.components.ContentInset
import com.newoether.rendrop.ui.components.ContentMaxWidth
import com.newoether.rendrop.ui.components.EmptyState
import com.newoether.rendrop.ui.components.GroupHeader
import com.newoether.rendrop.ui.components.GroupItem
import com.newoether.rendrop.ui.components.ListRow
import com.newoether.rendrop.ui.components.RendropDropdownMenu
import com.newoether.rendrop.ui.components.RendropDropdownMenuItem
import com.newoether.rendrop.ui.components.TitleAreaHeight
import com.newoether.rendrop.ui.components.TitleBarHeight
import com.newoether.rendrop.ui.components.rememberTitleFraction
import com.newoether.rendrop.ui.components.statusBarTop
import com.newoether.rendrop.ui.theme.MonoLabel

@Composable
fun DevicesPage(
    devices: List<Pair<String, String>>,
    listState: LazyListState,
    onEdit: (String, String) -> Unit,
    onDelete: (Pair<String, String>) -> Unit,
) {
    val fraction by rememberTitleFraction(listState)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ContentMaxWidth)
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = ContentInset),
            // Extra bottom room keeps the last row clear of the add FAB.
            contentPadding = PaddingValues(top = statusBarTop() + TitleBarHeight, bottom = BottomBarClearance + 80.dp)
        ) {
            item(key = "title_area") { Spacer(Modifier.height(TitleAreaHeight)) }
            if (devices.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.Computer,
                        message = stringResource(R.string.device_list_empty),
                        modifier = Modifier.fillParentMaxHeight(0.4f)
                    )
                }
            } else {
                item(key = "header_devices") {
                    GroupHeader(title = stringResource(R.string.devices), trailing = devices.size.toString())
                }
                itemsIndexed(devices, key = { _, d -> d.first }) { index, device ->
                    DeviceItem(
                        device = device,
                        index = index,
                        count = devices.size,
                        onEdit = { onEdit(device.first, device.second) },
                        onDelete = { onDelete(device) }
                    )
                }
            }
        }
        CollapsingTitleBar(title = stringResource(R.string.devices), fraction = fraction)
    }
}

/** Both the row and the trailing button open the edit/delete menu, each with its own ripple. */
@Composable
private fun DeviceItem(
    device: Pair<String, String>,
    index: Int,
    count: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    GroupItem(index = index, count = count, onClick = { showMenu = true }) {
        ListRow(
            headline = { Text(device.second, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supporting = { Text(device.first, style = MonoLabel) },
            leading = { Icon(Icons.Default.Computer, contentDescription = null) },
            trailing = {
                // The box anchors the menu at the trailing edge for both entry points.
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RendropDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        offset = DpOffset((-8).dp, 0.dp)
                    ) {
                        RendropDropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        RendropDropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        )
    }
}
