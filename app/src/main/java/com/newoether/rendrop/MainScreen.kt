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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    var projects = rememberSaveable(
        saver = listSaver(
            save = { list ->
                list.map { p ->
                    listOf(
                        p.id, p.name, p.path, p.outputPath, p.state,
                        p.frameStart, p.frameEnd, p.frameStep,
                        p.resolutionX, p.resolutionY, p.resolutionScale,
                        p.renderEngine, p.finishedFrame, p.totalFrame
                    )
                }
            },
            restore = { saved ->
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
                        totalFrame = l[13] as Int
                    )
                }.toMutableStateList()
            }
        )
    ) { mutableStateListOf<ProjectInfo>() } // 初始列表为空
    var devices by rememberSaveable { mutableStateOf(listOf<Pair<String, String>>()) }
    var projectRefreshing by rememberSaveable { mutableStateOf(false) }
    var deviceRefreshing by rememberSaveable { mutableStateOf(false) }

    var showScanDeviceDialog by rememberSaveable { mutableStateOf(false) }

    var showAddDeviceDialog by rememberSaveable { mutableStateOf(false) }
    var addIp by rememberSaveable { mutableStateOf("") }
    var addName by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            Crossfade(targetState = selectedTab) {
                when (it) {
                    0 -> TopAppBar(title = { Text("项目") })
                    1 -> TopAppBar(title = { Text("设备") })
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                    label = { Text("项目") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                    label = { Text("设备") }
                )
            }
        },
        floatingActionButton = {
            FabMenu(
                selectedTab = selectedTab,
                onShowScanDeviceDialog = {
                    showScanDeviceDialog = true
                },
                onShowAddDeviceDialog = {
                    showAddDeviceDialog = true
                    addIp = ""
                    addName = ""
                }
            )
        }
    ) { innerPadding ->
        Crossfade(targetState = selectedTab, modifier = Modifier.padding(innerPadding)) { screen ->
            when (screen) {
                0 -> ProjectList(
                    devices = devices,
                    projects = projects,
                    onProjectsChange = { projects = it.toMutableStateList() },
                    refreshing = projectRefreshing,
                    onRefreshingChange = { projectRefreshing = it }
                )
                1 -> DeviceList(
                    devices = devices,
                    onDevicesChange = { devices = it },
                    refreshing = deviceRefreshing,
                    onRefreshingChange = { deviceRefreshing = it }
                )
            }
        }

        ScanDeviceDialog(
            showScanDeviceDialog = showScanDeviceDialog,
            onShowScanDeviceDialogChange = { showScanDeviceDialog = it },
            devices = devices,
            onDevicesChange = { devices = it }
        )

        AddDeviceDialog(
            addIp = addIp,
            onAddIpChange = { addIp = it },
            addName = addName,
            onAddNameChange = { addName = it },
            showAddDeviceDialog = showAddDeviceDialog,
            onShowAddDeviceDialogChange = { showAddDeviceDialog = it },
            devices = devices,
            onDevicesChange = { devices = it }
        )
    }
}

@Composable
fun FabMenu(
    selectedTab: Int,
    onShowScanDeviceDialog: () -> Unit,
    onShowAddDeviceDialog: () -> Unit
) {
    data class FabMenuItem(
        val label: String,
        val icon: ImageVector,
        val width: Dp,
        val onClick: () -> Unit
    )

    val collapsedWidth = 56.dp
    val itemStagger = 80L

    var expanded by rememberSaveable { mutableStateOf(false) }
    val fabScale by animateFloatAsState(
        targetValue = if (selectedTab == 1) 1.0f else 0.5f,
        animationSpec =
            if (selectedTab == 1) {
                spring(
                    dampingRatio = 0.5f,
                    stiffness = 400f
                )
            }
            else {
                spring(
                    dampingRatio = 2.5f,
                    stiffness = 1500f
                )
            }
    )
    val fabAlpha by animateFloatAsState(targetValue = if (selectedTab == 1) 1f else 0f, animationSpec = tween(300))
    val fabRotation by animateFloatAsState(targetValue = if (expanded) 45f else 0f, animationSpec = tween(300))

    val menuItems = listOf(
        FabMenuItem(label = "扫描设备", icon = Icons.Default.Wifi, width = 155.dp) {
            onShowScanDeviceDialog()
            expanded = false
        },
        FabMenuItem(label = "添加设备", icon = Icons.Default.Create, width = 155.dp) {
            onShowAddDeviceDialog()
            expanded = false
        }
    )

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
                            targetValue = menuItems[index].width,
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = 500f
                            )
                        )
                    }
                    launch {
                        menuAlphas[index].animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 280)
                        )
                    }
                }
            }
        } else {
            menuItems.indices.forEachIndexed { order, idx ->
                val startDelay = order * itemStagger
                launch {
                    delay(startDelay)
                    launch { menuAlphas[idx].animateTo(0f, animationSpec = tween(durationMillis = 200)) }
                    launch { menuWidths[idx].animateTo(collapsedWidth, animationSpec = spring(dampingRatio = 1.5f, stiffness = 1500f)) }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(16.dp)
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
                    modifier = Modifier.size(64.dp)
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
                        modifier = Modifier.size(64.dp)
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
            modifier = Modifier.size(width, height)
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
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
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
    @Serializable
    data class DeviceItem(val ip: String, val name: String, val checked: Boolean, val enabled: Boolean)

    var loading by rememberSaveable { mutableStateOf(false) }
    var scanDevices = rememberSaveable(
        saver = listSaver(
            save = { list -> list.map { listOf(it.ip, it.name, it.checked) } },
            restore = { saved ->
                saved.map {
                    val l = it as List<Any>
                    DeviceItem(l[0] as String, l[1] as String, l[2] as Boolean, l[3] as Boolean)
                }.toMutableStateList()
            }
        )
    ) { mutableStateListOf<DeviceItem>() }
    val scope = rememberCoroutineScope()
    val windowSize = LocalWindowInfo.current.containerSize
    val screenHeight = with(LocalDensity.current) { windowSize.height.toDp() }

    suspend fun refresh() {
        if (loading) return
        loading = true
        val result = withContext(Dispatchers.IO) { scanLanDevices() }
        val newDevices = result.map {
            DeviceItem(
                ip = it.first,
                name = it.second,
                checked = false,
                enabled = true
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

        scanDevices = newDevices.toMutableStateList()
        loading = false
    }

    LaunchedEffect(showScanDeviceDialog) {
        loading = false
        if (showScanDeviceDialog) {
            scanDevices = SnapshotStateList()
            refresh()
        }
    }

    if (showScanDeviceDialog) {
        AlertDialog(
            onDismissRequest = { onShowScanDeviceDialogChange(false) },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                ) {
                    Text("选择设备", style = MaterialTheme.typography.titleLarge)
                    IconButton(
                        onClick = {
                            scope.launch {
                                refresh()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            },
            text = {
                Column {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = loading,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (scanDevices.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .height(max(screenHeight * 0.3f, 100.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("未找到设备", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else {
                        Box(
                            modifier = Modifier.fillMaxWidth()
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
                                                scanDevices[index] = scanDevices[index].copy(checked = !scanDevices[index].checked)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            modifier = Modifier.size(24.dp),
                                            checked = device.checked,
                                            enabled = device.enabled,
                                            onCheckedChange = { isChecked ->
                                                scanDevices[index] = scanDevices[index].copy(checked = isChecked)
                                            }
                                        )
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val checkedDevices = scanDevices.filter { it.checked && it.enabled }
                            .map { Pair(it.ip, it.name) }
                        onDevicesChange(devices + checkedDevices)
                        onShowScanDeviceDialogChange(false)
                    },
                    enabled = true
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onShowScanDeviceDialogChange(false)
                    }
                ) {
                    Text("取消")
                }
            }
        )
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
            title = { Text("添加设备") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addIp,
                        onValueChange = { onAddIpChange(it) },
                        label = { Text("IP 地址") },
                        singleLine = true,
                        isError = addIp.isNotEmpty() && !isAddIpValid || isDuplicated
                    )
                    if (addIp.isNotEmpty() && !isAddIpValid) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "IP 格式不正确",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else if (isDuplicated)
                    {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "该 IP 已存在",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { onAddNameChange(it) },
                        label = { Text("设备名称") },
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
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onShowAddDeviceDialogChange(false)
                    }
                ) {
                    Text("取消")
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
    refreshing: Boolean,
    onRefreshingChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    fun loadDevices() {
        scope.launch {
            onRefreshingChange(true)
            delay(100)
            onRefreshingChange(false)
        }
    }

    LaunchedEffect(Unit) { loadDevices() }
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { loadDevices() },
        state = refreshState,
        modifier = Modifier.fillMaxSize()
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
                        Text("设备列表为空", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(devices) { device ->
                    Card(
                        onClick = {},
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                Icon(Icons.Default.Devices, contentDescription = null)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(device.second, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(3.dp))
                                Text(device.first, style = MaterialTheme.typography.bodySmall)
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
    onRefreshingChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    fun loadProjects() {
        scope.launch {
            onRefreshingChange(true)
            val ipList = devices.map { it.first }
            val allProjects = withContext(Dispatchers.IO) { fetchAllProjects(ipList) }
            delay(100)
            onProjectsChange(allProjects)
            onRefreshingChange(false)
        }
    }

    LaunchedEffect(Unit) { loadProjects() }
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { loadProjects() },
        state = refreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (projects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("项目列表为空", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(projects) { project ->
                    Card(
                        onClick = {},
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Checklist, contentDescription = null)
                                Text(project.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (project.totalFrame == 0) 0f else project.finishedFrame / project.totalFrame.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                strokeCap = StrokeCap.Round,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("${project.finishedFrame}/${project.totalFrame} 帧完成", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}