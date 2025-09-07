package com.newoether.rendrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import com.newoether.rendrop.ui.theme.RendropTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RendropTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    var projects by rememberSaveable { mutableStateOf(listOf<ProjectInfo>()) }
    var devices by rememberSaveable { mutableStateOf(listOf<String>()) }
    var projectRefreshing by rememberSaveable { mutableStateOf(false) }
    var deviceRefreshing by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (selectedTab == 0) "活动项目" else "设备") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                    label = { Text("活动项目") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                    label = { Text("设备") }
                )
            }
        }
    ) { innerPadding ->
        Crossfade(targetState = selectedTab, modifier = Modifier.padding(innerPadding)) { screen ->
            when (screen) {
                0 -> ProjectList(
                    projects = projects,
                    onProjectsChange = { projects = it },
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceList(
    devices: List<String>,
    onDevicesChange: (List<String>) -> Unit,
    refreshing: Boolean,
    onRefreshingChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    fun loadDevices() {
        scope.launch {
            onRefreshingChange(true)
            val scanned = withContext(Dispatchers.IO) { scanLanDevices() }
            onDevicesChange(scanned)
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
                        Text("暂无设备在线", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(devices) { device ->
                    Card(
                        onClick = { /* TODO 点击事件 */ },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Devices, contentDescription = null)
                            Text(device, style = MaterialTheme.typography.titleMedium)
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
    projects: List<ProjectInfo>,
    onProjectsChange: (List<ProjectInfo>) -> Unit,
    refreshing: Boolean,
    onRefreshingChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    fun loadProjects() {
        scope.launch {
            onRefreshingChange(true)
            val devices = withContext(Dispatchers.IO) { scanLanDevices() }
            val allProjects = withContext(Dispatchers.IO) { fetchAllProjects(devices) }
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
                        Text("暂无活动项目", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(projects) { project ->
                    Card(
                        onClick = { /* TODO 点击事件 */ },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Checklist, contentDescription = null)
                                Text(project.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (project.totalFrame == 0) 0f else project.finishedFrame / project.totalFrame.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                strokeCap = StrokeCap.Round,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${project.finishedFrame}/${project.totalFrame} 帧完成", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
