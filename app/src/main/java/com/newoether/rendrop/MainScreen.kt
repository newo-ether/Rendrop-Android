package com.newoether.rendrop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import com.newoether.rendrop.ui.components.FabMenu
import com.newoether.rendrop.ui.components.FabMenuAction
import com.newoether.rendrop.ui.components.PillTabSwitcher
import kotlinx.coroutines.launch

/** Vertical room the floating tab capsule takes at the bottom of each page. */
internal val BottomBarClearance = 104.dp

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = context.projectRepository
    val repositoryState by repository.state.collectAsState()
    val projects = repositoryState.projects
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedProjectKey by remember { mutableStateOf<ProjectKey?>(null) }
    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val dialogs = rememberDeviceDialogState()

    LaunchedEffect(Unit) {
        DeviceManager.getDevices(context).collect { devices = it }
    }

    fun updateDevices(newDevices: List<Pair<String, String>>) {
        devices = newDevices
        scope.launch { DeviceManager.saveDevices(context, newDevices) }
    }

    LaunchedEffect(projects, selectedProjectKey) {
        val key = selectedProjectKey ?: return@LaunchedEffect
        if (projects.none { it.deviceIp == key.deviceIp && it.id == key.id }) {
            selectedProjectKey = null
        }
    }

    val projectListState = rememberLazyListState()
    val deviceListState = rememberLazyListState()

    AnimatedContent(
        targetState = selectedProjectKey,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "Navigation"
    ) { projectKey ->
        if (projectKey != null) {
            val projectInfo = projects.firstOrNull {
                it.deviceIp == projectKey.deviceIp && it.id == projectKey.id
            }
            if (projectInfo != null) {
                ProjectDetailScreen(
                    initialProject = projectInfo,
                    onBack = { selectedProjectKey = null },
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Crossfade(targetState = selectedTab, label = "Tabs") { tab ->
                    when (tab) {
                        0 -> ProjectsPage(
                            devices = devices,
                            state = repositoryState,
                            listState = projectListState,
                            onRefresh = { scope.launch { repository.refresh() } },
                            onProjectClick = { selectedProjectKey = ProjectKey(it.deviceIp, it.id) },
                            onAddDevice = { selectedTab = 1 },
                        )
                        else -> DevicesPage(
                            devices = devices,
                            listState = deviceListState,
                            onEdit = { ip, name -> dialogs.openEdit(ip, name) },
                            onDelete = { dialogs.openDelete(it) },
                        )
                    }
                }
                FabMenu(
                    visible = selectedTab == 1,
                    actions = listOf(
                        FabMenuAction(stringResource(R.string.scan_device), Icons.Default.Wifi) { dialogs.showScan = true },
                        FabMenuAction(stringResource(R.string.add_device), Icons.Default.Create) { dialogs.openAdd() },
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = BottomBarClearance)
                )
                BottomTabBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                DeviceDialogs(state = dialogs, devices = devices, onDevicesChange = { updateDevices(it) })
            }
        }
    }
}

/** Floating capsule holding Agora's pill tab switcher. */
@Composable
private fun BottomTabBar(selectedTab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .width(240.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        PillTabSwitcher(
            tabs = listOf(stringResource(R.string.projects), stringResource(R.string.devices)),
            icons = listOf(Icons.Default.Checklist, Icons.Default.Devices),
            selectedIndex = selectedTab,
            onSelect = onSelect,
            modifier = Modifier.padding(6.dp),
        )
    }
}
