package com.newoether.rendrop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedProject by remember { mutableStateOf<ProjectInfo?>(null) }

    val projects = rememberSaveable(saver = listSaver(save = { list ->
        list.map { p ->
            listOf(
                p.id,
                p.name,
                p.path,
                p.outputPath,
                p.state,
                p.frameStart,
                p.frameEnd,
                p.frameStep,
                p.resolutionX,
                p.resolutionY,
                p.resolutionScale,
                p.renderEngine,
                p.finishedFrame,
                p.totalFrame,
                p.deviceIp
            )
        }
    }, restore = { saved ->
        saved.map { s ->
            val l = s as List<Any>
            ProjectInfo(
                id = l[0] as Int,
                name = l[1] as String,
                path = l[2] as String,
                outputPath = l[3] as String,
                state = l[4] as String,
                frameStart = l[5] as Int,
                frameEnd = l[6] as Int,
                frameStep = l[7] as Int,
                resolutionX = l[8] as Int,
                resolutionY = l[9] as Int,
                resolutionScale = l[10] as Int,
                renderEngine = l[11] as String,
                finishedFrame = l[12] as Int,
                totalFrame = l[13] as Int,
                deviceIp = if (l.size > 14) l[14] as String else ""
            )
        }.toMutableStateList()
    })) { mutableStateListOf<ProjectInfo>() }

    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var projectRefreshing by rememberSaveable { mutableStateOf(false) }
    var deviceRefreshing by rememberSaveable { mutableStateOf(false) }

    var showScanDeviceDialog by rememberSaveable { mutableStateOf(false) }
    var showAddDeviceDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDeviceDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDeviceDialog by rememberSaveable { mutableStateOf(false) }

    var addIp by rememberSaveable { mutableStateOf("") }
    var addName by rememberSaveable { mutableStateOf("") }

    var editingDeviceIp by rememberSaveable { mutableStateOf("") }
    var editingDeviceName by rememberSaveable { mutableStateOf("") }
    var originalDeviceIp by rememberSaveable { mutableStateOf("") }

    var deletingDevice by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Load devices from DataStore
    LaunchedEffect(Unit) {
        DeviceManager.getDevices(context).collect { loadedDevices ->
            devices = loadedDevices
        }
    }

    fun updateDevices(newDevices: List<Pair<String, String>>) {
        devices = newDevices
        scope.launch {
            DeviceManager.saveDevices(context, newDevices)
        }
    }

    AnimatedContent(
        targetState = selectedProject, transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
            } else {
                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
            }
        }, label = "Navigation"
    ) { projectInfo ->
        if (projectInfo != null) {
            ProjectDetailScreen(
                initialProject = projectInfo,
                onBack = { selectedProject = null },
                onProjectUpdate = { updated ->
                    val index =
                        projects.indexOfFirst { it.id == updated.id && it.deviceIp == updated.deviceIp }
                    if (index != -1) {
                        projects[index] = updated
                    }
                })
        } else {
                        Scaffold(topBar = {
                            Crossfade(targetState = selectedTab) {
                                when (it) {
                                    0 -> TopAppBar(title = { Text(stringResource(R.string.projects)) })
                                    1 -> TopAppBar(title = { Text(stringResource(R.string.devices)) })
                                }
                            }
                        }, bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                                    label = { Text(stringResource(R.string.projects)) })
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                    label = { Text(stringResource(R.string.devices)) })
                            }
                        },
             floatingActionButton = {
                FabMenu(selectedTab = selectedTab, onShowScanDeviceDialog = {
                    showScanDeviceDialog = true
                }, onShowAddDeviceDialog = {
                    showAddDeviceDialog = true
                    addIp = ""
                    addName = ""
                })
            }) { innerPadding ->
                Crossfade(
                    targetState = selectedTab, modifier = Modifier.padding(innerPadding)
                ) { screen ->
                    when (screen) {
                        0 -> ProjectList(
                            devices = devices,
                            projects = projects,
                            onProjectsChange = {
                                projects.clear()
                                projects.addAll(it)
                            },
                            refreshing = projectRefreshing,
                            onRefreshingChange = { projectRefreshing = it },
                            onProjectClick = { selectedProject = it })

                        1 -> DeviceList(
                            devices = devices,
                            onDevicesChange = { updateDevices(it) },
                            projects = projects,
                            onProjectsChange = { projects.clear(); projects.addAll(it) },
                            refreshing = deviceRefreshing,
                            onRefreshingChange = { deviceRefreshing = it },
                            onEditDevice = { ip, name ->
                                originalDeviceIp = ip
                                editingDeviceIp = ip
                                editingDeviceName = name
                                showEditDeviceDialog = true
                            },
                            onDeleteDevice = { device ->
                                deletingDevice = device
                                showDeleteDeviceDialog = true
                            })

                    }
                }

                ScanDeviceDialog(
                    showScanDeviceDialog = showScanDeviceDialog,
                    onShowScanDeviceDialogChange = { showScanDeviceDialog = it },
                    devices = devices,
                    onDevicesChange = { updateDevices(it) })

                AddDeviceDialog(
                    addIp = addIp,
                    onAddIpChange = { addIp = it },
                    addName = addName,
                    onAddNameChange = { addName = it },
                    showAddDeviceDialog = showAddDeviceDialog,
                    onShowAddDeviceDialogChange = { showAddDeviceDialog = it },
                    devices = devices,
                    onDevicesChange = { updateDevices(it) })

                EditDeviceDialog(
                    ip = editingDeviceIp,
                    onIpChange = { editingDeviceIp = it },
                    name = editingDeviceName,
                    onNameChange = { editingDeviceName = it },
                    showDialog = showEditDeviceDialog,
                    onShowDialogChange = { showEditDeviceDialog = it },
                    originalIp = originalDeviceIp,
                    devices = devices,
                    onDevicesChange = { updateDevices(it) })

                DeleteConfirmationDialog(
                    showDialog = showDeleteDeviceDialog,
                    onShowDialogChange = { showDeleteDeviceDialog = it },
                    device = deletingDevice,
                    onConfirm = { device ->
                        updateDevices(devices.filter { it.first != device.first })
                        projects.removeIf { it.deviceIp == device.first }
                    })
            }
        }

    }
}

@Composable
fun FabMenu(
    selectedTab: Int, onShowScanDeviceDialog: () -> Unit, onShowAddDeviceDialog: () -> Unit
) {
    data class FabMenuItem(
        val label: String, val icon: ImageVector, val width: Dp, val onClick: () -> Unit
    )

    val collapsedWidth = dimensionResource(R.dimen.fab_collapsed_width)
    val expandedWidth = dimensionResource(R.dimen.fab_expanded_width)
    val itemStagger = 80L

    var expanded by rememberSaveable { mutableStateOf(false) }
    val fabScale by animateFloatAsState(
        targetValue = if (selectedTab == 1) 1.0f else 0.5f, animationSpec = if (selectedTab == 1) {
            spring(
                dampingRatio = 0.5f, stiffness = 400f
            )
        } else {
            spring(
                dampingRatio = 2.5f, stiffness = 1500f
            )
        }
    )
    val fabAlpha by animateFloatAsState(
        targetValue = if (selectedTab == 1) 1f else 0f, animationSpec = tween(300)
    )
    val fabRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f, animationSpec = tween(300)
    )

    val menuItems =
        listOf(FabMenuItem(label = stringResource(R.string.scan_device), icon = Icons.Default.Wifi, width = expandedWidth) {
            onShowScanDeviceDialog()
            expanded = false
        }, FabMenuItem(label = stringResource(R.string.add_device), icon = Icons.Default.Create, width = expandedWidth) {
            onShowAddDeviceDialog()
            expanded = false
        })

    val menuWidths = remember {
        menuItems.map { Animatable(collapsedWidth, Dp.VectorConverter) }
    }
    val menuAlphas = remember {
        menuItems.map { Animatable(0f) }
    }

    expanded = expanded && (selectedTab == 1)

    LaunchedEffect(expanded) {
        if (expanded) {
            menuItems.indices.reversed().forEachIndexed { order, index ->
                val startDelay = order * itemStagger
                launch {
                    delay(startDelay)
                    launch {
                        menuWidths[index].animateTo(
                            targetValue = menuItems[index].width, animationSpec = spring(
                                dampingRatio = 0.7f, stiffness = 500f
                            )
                        )
                    }
                    launch {
                        menuAlphas[index].animateTo(
                            targetValue = 1f, animationSpec = tween(durationMillis = 280)
                        )
                    }
                }
            }
        } else {
            menuItems.indices.forEachIndexed { order, idx ->
                val startDelay = order * itemStagger
                launch {
                    delay(startDelay)
                    launch {
                        menuAlphas[idx].animateTo(
                            0f, animationSpec = tween(durationMillis = 200)
                        )
                    }
                    launch {
                        menuWidths[idx].animateTo(
                            collapsedWidth,
                            animationSpec = spring(dampingRatio = 1.5f, stiffness = 1500f)
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End, modifier = Modifier.padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                menuItems.forEachIndexed { index, item ->
                    AnimatedExtendedFab(
                        text = item.label,
                        icon = item.icon,
                        width = menuWidths[index].value,
                        alpha = menuAlphas[index].value,
                        onClick = item.onClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (fabAlpha > 0) {
                val shadowColor = DefaultShadowColor.copy(alpha = fabAlpha.pow(8))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(fabScale)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(31.dp),
                            clip = false,
                            ambientColor = shadowColor,
                            spotColor = shadowColor
                        )
                ) {
                    FloatingActionButton(
                        onClick = { expanded = !expanded },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(64.dp)
                            .scale(fabScale)
                            .alpha(fabAlpha),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.rotate(fabRotation)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedExtendedFab(
    text: String,
    icon: ImageVector,
    width: Dp,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    if (alpha > 0) {
        val shadowColor = DefaultShadowColor.copy(alpha = alpha)
        Box(
            modifier = Modifier
                .size(width, height)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(28.dp),
                    clip = false,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
        ) {
            Surface(
                modifier = modifier
                    .width(width)
                    .height(height)
                    .alpha(alpha)
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(24.dp))
                    Icon(
                        icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

@Composable
fun ScanDeviceDialog(
    showScanDeviceDialog: Boolean,
    onShowScanDeviceDialogChange: (Boolean) -> Unit,
    devices: List<Pair<String, String>>,
    onDevicesChange: (List<Pair<String, String>>) -> Unit
) {
    val context = LocalContext.current

    @Serializable
    data class DeviceItem(
        val ip: String, val name: String, val checked: Boolean, val enabled: Boolean
    )

    var loading by rememberSaveable { mutableStateOf(false) }
    val scanDevices = rememberSaveable(
        saver = listSaver(
            save = { list ->
            list.map {
                listOf(
                    it.ip,
                    it.name,
                    it.checked,
                    it.enabled
                )
            }
        },
        restore = { saved ->
            saved.map {
                val l = it as List<Any>
                DeviceItem(l[0] as String, l[1] as String, l[2] as Boolean, l[3] as Boolean)
            }.toMutableStateList()
        })) { mutableStateListOf<DeviceItem>() }
    val scope = rememberCoroutineScope()
    val windowSize = LocalWindowInfo.current.containerSize
    val screenHeight = with(LocalDensity.current) { windowSize.height.toDp() }

    suspend fun refresh() {
        if (loading) return
        loading = true
        val result = withContext(Dispatchers.IO) { scanLanDevices(context) }
        val newDevices = result.map {
            DeviceItem(
                ip = it.first, name = it.second, checked = false, enabled = true
            )
        }.toMutableList()

        newDevices.forEachIndexed { index, device ->
            if (devices.find { it.first == device.ip } != null) {
                newDevices[index] = device.copy(checked = true, enabled = false)
            }
        }

        scanDevices.forEach { device ->
            if (device.checked) {
                newDevices.find { it.ip == device.ip }?.let { matched ->
                    val index = newDevices.indexOf(matched)
                    newDevices[index] = matched.copy(checked = true)
                }
            }
        }

        scanDevices.clear()
        scanDevices.addAll(newDevices)
        loading = false
    }

    LaunchedEffect(showScanDeviceDialog) {
        if (showScanDeviceDialog) {
            loading = false
            scanDevices.clear()
            refresh()
        }
    }

    if (showScanDeviceDialog) {
        AlertDialog(onDismissRequest = { onShowScanDeviceDialogChange(false) }, title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                Text(stringResource(R.string.select_device), style = MaterialTheme.typography.titleLarge)
                IconButton(
                    onClick = {
                        scope.launch {
                            refresh()
                        }
                    }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }, text = {
            Column {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = loading, enter = fadeIn(), exit = fadeOut()
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (scanDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(max(screenHeight * 0.3f, 100.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_devices_found), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(max(screenHeight * 0.3f, 100.dp))
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier.verticalScroll(scrollState)
                        ) {
                            scanDevices.forEachIndexed { index, device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = device.enabled) {
                                            scanDevices[index] =
                                                scanDevices[index].copy(checked = !scanDevices[index].checked)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        modifier = Modifier.size(24.dp),
                                        checked = device.checked,
                                        enabled = device.enabled,
                                        onCheckedChange = { isChecked ->
                                            scanDevices[index] =
                                                scanDevices[index].copy(checked = isChecked)
                                        })
                                    Spacer(Modifier.width(12.dp))
                                    Icon(Icons.Default.Devices, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            device.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            device.ip,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = {
            TextButton(
                onClick = {
                    val checkedDevices = scanDevices.filter { it.checked && it.enabled }
                        .map { Pair(it.ip, it.name) }
                    onDevicesChange(devices + checkedDevices)
                    onShowScanDeviceDialogChange(false)
                }, enabled = true
            ) {
                Text(stringResource(R.string.ok))
            }
        }, dismissButton = {
            TextButton(
                onClick = {
                    onShowScanDeviceDialogChange(false)
                }) {
                Text(stringResource(R.string.cancel))
            }
        })
    }
}

@Composable
fun AddDeviceDialog(
    addIp: String,
    onAddIpChange: (String) -> Unit,
    addName: String,
    onAddNameChange: (String) -> Unit,
    showAddDeviceDialog: Boolean,
    onShowAddDeviceDialogChange: (Boolean) -> Unit,
    devices: List<Pair<String, String>>,
    onDevicesChange: (List<Pair<String, String>>) -> Unit
) {
    if (showAddDeviceDialog) {
        val isAddIpValid = isValidIp(addIp)
        val item = devices.find { it.first == addIp }
        val isDuplicated = item != null

        AlertDialog(
            onDismissRequest = { onShowAddDeviceDialogChange(false) },
            title = { Text(stringResource(R.string.add_device)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = addIp,
                        onValueChange = { onAddIpChange(it) },
                        label = { Text(stringResource(R.string.ip_address)) },
                        singleLine = true,
                        isError = addIp.isNotEmpty() && !isAddIpValid || isDuplicated
                    )
                    if (addIp.isNotEmpty() && !isAddIpValid) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.invalid_ip),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (isDuplicated) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ip_exists),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { onAddNameChange(it) },
                        label = { Text(stringResource(R.string.device_name)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val modifiedDevices = devices.toMutableList()
                        modifiedDevices.add(Pair(addIp, addName))
                        onDevicesChange(modifiedDevices.toList())
                        onShowAddDeviceDialogChange(false)
                    },
                    enabled = addIp.isNotEmpty() && isAddIpValid && addName.isNotEmpty() && !isDuplicated
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onShowAddDeviceDialogChange(false)
                    }) {
                    Text(stringResource(R.string.cancel))
                }
            })
    }
}

@Composable
fun EditDeviceDialog(
    ip: String,
    onIpChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    showDialog: Boolean,
    onShowDialogChange: (Boolean) -> Unit,
    originalIp: String,
    devices: List<Pair<String, String>>,
    onDevicesChange: (List<Pair<String, String>>) -> Unit
) {
    if (showDialog) {
        val isIpValid = isValidIp(ip)
        val isDuplicated = devices.any { it.first == ip && it.first != originalIp }
        AlertDialog(
            onDismissRequest = { onShowDialogChange(false) },
            title = { Text(stringResource(R.string.edit_device)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { onIpChange(it) },
                        label = { Text(stringResource(R.string.ip_address)) },
                        singleLine = true,
                        isError = ip.isNotEmpty() && !isIpValid || isDuplicated
                    )
                    if (ip.isNotEmpty() && !isIpValid) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.invalid_ip),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (isDuplicated) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ip_exists),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { onNameChange(it) },
                        label = { Text(stringResource(R.string.device_name)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newDevices = devices.map {
                            if (it.first == originalIp) ip to name else it
                        }
                        onDevicesChange(newDevices)
                        onShowDialogChange(false)
                    },
                    enabled = ip.isNotEmpty() && isIpValid && name.isNotEmpty() && !isDuplicated
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onShowDialogChange(false) }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable

fun DeviceList(
    devices: List<Pair<String, String>>,
    onDevicesChange: (List<Pair<String, String>>) -> Unit,
    projects: List<ProjectInfo>,
    onProjectsChange: (List<ProjectInfo>) -> Unit,
    refreshing: Boolean,
    onRefreshingChange: (Boolean) -> Unit,
    onEditDevice: (String, String) -> Unit,
    onDeleteDevice: (Pair<String, String>) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (devices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.device_list_empty), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            items(
                items = devices,
                key = { it.first } // Use IP as stable key for animation
            ) { device ->
                Card(
                    onClick = { /* Nothing happens on card click */ },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.second, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(3.dp))
                            Text(device.first, style = MaterialTheme.typography.bodySmall)
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = {
                                        showMenu = false
                                        onEditDevice(device.first, device.second)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Edit, contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.delete), color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDeleteDevice(device)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectList(
    devices: List<Pair<String, String>>,
    projects: List<ProjectInfo>,
    onProjectsChange: (List<ProjectInfo>) -> Unit,
    refreshing: Boolean,
    onRefreshingChange: (Boolean) -> Unit,
    onProjectClick: (ProjectInfo) -> Unit
) {
    val scope = rememberCoroutineScope()

    fun loadProjects(showLoading: Boolean = true) {
        scope.launch {
            val startTime = System.currentTimeMillis()
            if (showLoading) onRefreshingChange(true)
            try {
                val ipList = devices.map { it.first }
                if (ipList.isNotEmpty()) {
                    val allProjects = withContext(Dispatchers.IO) { fetchAllProjects(ipList) }
                    val currentIps = devices.map { it.first }.toSet()
                    onProjectsChange(allProjects.filter { it.deviceIp in currentIps })
                }
            } finally {
                if (showLoading) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < 200) {
                        delay(200 - elapsedTime)
                    }
                    onRefreshingChange(false)
                }
            }
        }
    }

    // Auto refresh: always silent
    LaunchedEffect(devices) {
        if (devices.isNotEmpty()) {
            while (true) {
                loadProjects(showLoading = false)
                delay(3000)
            }
        }
    }

    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { loadProjects() },
        state = refreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        val groupedProjects = projects.groupBy { it.deviceIp }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (projects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.project_list_empty), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                devices.forEach { (ip, name) ->
                    val deviceProjects = groupedProjects[ip]
                    if (!deviceProjects.isNullOrEmpty()) {
                        item(key = "header_$ip") {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Devices,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$name ($ip)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        items(
                            items = deviceProjects,
                            key = { "${it.deviceIp}_${it.id}" }) { project ->
                            ProjectCard(
                                project = project, onClick = { onProjectClick(it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    showDialog: Boolean,
    onShowDialogChange: (Boolean) -> Unit,
    device: Pair<String, String>?,
    onConfirm: (Pair<String, String>) -> Unit
) {
    if (showDialog && device != null) {
        AlertDialog(
            onDismissRequest = { onShowDialogChange(false) },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.delete_device_msg, device.second, device.first)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(device)
                        onShowDialogChange(false)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowDialogChange(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ProjectCard(project: ProjectInfo, onClick: (ProjectInfo) -> Unit) {
    Card(
        onClick = { onClick(project) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = null)
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val (containerColor, contentColor, statusRes) = when (project.state.lowercase()) {
                    "rendering" -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, R.string.status_rendering)
                    "finished" -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, R.string.status_finished)
                    "error" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, R.string.status_error)
                    "queued" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, R.string.status_queued)
                    "loading" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, R.string.status_loading)
                    else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, null)
                }

                Surface(
                    color = containerColor,
                    contentColor = contentColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (statusRes != null) stringResource(statusRes).uppercase() else project.state.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thumbnail
                val state = project.state.lowercase()
                val isActive = state == "rendering"
                val isWaiting = state == "queued" || state == "loading"
                val isError = state == "error"

                // Capture colors outside drawWithContent lambda
                val surfaceColor = MaterialTheme.colorScheme.surface
                val errorColor = MaterialTheme.colorScheme.error

                val displayFrame = if (project.finishedFrame > 0) {
                    (project.finishedFrame - 1) * project.frameStep + project.frameStart
                } else null

                val thumbnailUrl = displayFrame?.let {
                    "http://${project.deviceIp}:28528/frame?id=${project.id}&frame=$it&thumb=1"
                }

                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = when {
                        isActive -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        isError -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                        isWaiting -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        else -> null
                    }
                ) {
                    SubcomposeAsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Project Thumbnail",
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                drawContent()
                                if (isWaiting) {
                                    drawRect(surfaceColor.copy(alpha = 0.4f))
                                } else if (isError) {
                                    drawRect(errorColor.copy(alpha = 0.1f))
                                }
                            },
                        contentScale = ContentScale.Crop,
                        colorFilter = if (isWaiting) ColorFilter.colorMatrix(ColorMatrix().apply {
                            setToSaturation(
                                0.3f
                            )
                        }) else null,
                        alpha = if (isWaiting) 0.8f else 1.0f,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        })
                }

                Column(modifier = Modifier.weight(1f)) {
                    val progress =
                        if (project.totalFrame == 0) 0f else project.finishedFrame / project.totalFrame.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = (if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(
                            alpha = 0.2f
                        ),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.frame_count, project.finishedFrame, project.totalFrame),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        project.renderEngine,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${project.resolutionX}x${project.resolutionY} (${project.resolutionScale}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}