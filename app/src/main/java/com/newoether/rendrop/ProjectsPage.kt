package com.newoether.rendrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.newoether.rendrop.ui.components.ActionRow
import com.newoether.rendrop.ui.components.CollapsingTitleBar
import com.newoether.rendrop.ui.components.ContentInset
import com.newoether.rendrop.ui.components.ContentMaxWidth
import com.newoether.rendrop.ui.components.EmptyState
import com.newoether.rendrop.ui.components.GroupHeader
import com.newoether.rendrop.ui.components.GroupItem
import com.newoether.rendrop.ui.components.ListRow
import com.newoether.rendrop.ui.components.StatusPill
import com.newoether.rendrop.ui.components.TitleAreaHeight
import com.newoether.rendrop.ui.components.TitleBarHeight
import com.newoether.rendrop.ui.components.TitledPullRefresh
import com.newoether.rendrop.ui.components.rememberTitleFraction
import com.newoether.rendrop.ui.components.statusBarTop
import com.newoether.rendrop.ui.theme.MonoLabel

@Composable
fun ProjectsPage(
    devices: List<Pair<String, String>>,
    state: ProjectRepositoryState,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onProjectClick: (ProjectInfo) -> Unit,
    onAddDevice: () -> Unit,
) {
    val fraction by rememberTitleFraction(listState)
    val grouped = state.projects.groupBy { it.deviceIp }
    // Unreachable devices read as offline in their own group; only real faults get the red card.
    val offline = state.errors.filterValues { it is RendropError.Network }.keys
    val faults = state.errors.filterValues { it !is RendropError.Network }

    Box(Modifier.fillMaxSize()) {
        TitledPullRefresh(refreshing = state.isRefreshing, onRefresh = onRefresh) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = ContentInset),
                contentPadding = PaddingValues(top = statusBarTop() + TitleBarHeight, bottom = BottomBarClearance)
            ) {
                item(key = "title_area") { Spacer(Modifier.height(TitleAreaHeight)) }
                if (faults.isNotEmpty()) {
                    item(key = "errors") { ErrorCard(faults) }
                }
                when {
                    devices.isEmpty() -> item(key = "no_devices") {
                        Column(Modifier.fillParentMaxHeight(0.6f)) {
                            EmptyState(
                                icon = Icons.Default.Computer,
                                message = stringResource(R.string.device_list_empty),
                                modifier = Modifier.weight(1f)
                            )
                            GroupItem(index = 0, count = 1) {
                                ActionRow(stringResource(R.string.add_device), Icons.Default.Add, onAddDevice)
                            }
                        }
                    }
                    else -> devices.forEachIndexed { groupIndex, (ip, name) ->
                        val deviceProjects = grouped[ip].orEmpty()
                        item(key = "header_$ip") {
                            // Extra gap separates one device's group from the previous one.
                            GroupHeader(
                                title = name,
                                leadingIcon = Icons.Default.Computer,
                                trailing = ip,
                                modifier = Modifier.padding(top = if (groupIndex > 0) 16.dp else 0.dp)
                            )
                        }
                        when {
                            ip in offline -> item(key = "offline_$ip") {
                                DeviceNoticeRow(Icons.Default.CloudOff, stringResource(R.string.device_offline))
                            }
                            deviceProjects.isEmpty() -> item(key = "empty_$ip") {
                                DeviceNoticeRow(Icons.Default.Inventory2, stringResource(R.string.project_list_empty))
                            }
                        }
                        itemsIndexed(deviceProjects, key = { _, p -> "${p.deviceIp}_${p.id}" }) { index, project ->
                            GroupItem(index = index, count = deviceProjects.size, onClick = { onProjectClick(project) }) {
                                ProjectRow(project)
                            }
                        }
                    }
                }
            }
            // Inside the refresh box so the indicator draws above the opaque title bar.
            CollapsingTitleBar(title = stringResource(R.string.projects), fraction = fraction)
        }
    }
}

/** Single muted row standing in for a device's queue when it is offline or empty. */
@Composable
private fun DeviceNoticeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    GroupItem(index = 0, count = 1) {
        ListRow(
            headline = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
    }
}

@Composable
private fun ErrorCard(errors: Map<String, RendropError>) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                errors.forEach { (ip, error) ->
                    Text(projectErrorMessage(ip, error), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun projectErrorMessage(ip: String, error: RendropError): String {
    val noDetail = stringResource(R.string.project_error_no_detail)
    return when (error) {
        is RendropError.Network -> stringResource(
            R.string.project_network_error, ip, error.detail?.takeIf { it.isNotBlank() } ?: noDetail
        )
        is RendropError.Http -> stringResource(R.string.project_http_error, ip, error.statusCode)
        is RendropError.Protocol -> stringResource(
            R.string.project_protocol_error, ip, error.detail?.takeIf { it.isNotBlank() } ?: noDetail
        )
        is RendropError.Unexpected -> stringResource(
            R.string.project_unknown_error, ip, error.detail?.takeIf { it.isNotBlank() } ?: noDetail
        )
    }
}

/** Container/content colors and label for a project state, shared with the detail screen. */
@Composable
internal fun projectStatusStyle(state: String): Triple<Color, Color, String> {
    val scheme = MaterialTheme.colorScheme
    return when (state.lowercase()) {
        "rendering" -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, stringResource(R.string.status_rendering))
        "finished" -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, stringResource(R.string.status_finished))
        "error" -> Triple(scheme.errorContainer, scheme.onErrorContainer, stringResource(R.string.status_error))
        "queued" -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, stringResource(R.string.status_queued))
        "loading" -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, stringResource(R.string.status_loading))
        else -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, state)
    }
}

@Composable
private fun ProjectRow(project: ProjectInfo) {
    val state = project.state.lowercase()
    val isWaiting = state == "queued" || state == "loading"
    val isError = state == "error"
    val progress = if (project.totalFrame == 0) 0f else project.finishedFrame / project.totalFrame.toFloat()
    val (container, content, label) = projectStatusStyle(project.state)

    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProjectThumbnail(project, dimmed = isWaiting)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(label, container, content)
            }
            Spacer(Modifier.height(10.dp))
            val barColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.frame_count, project.finishedFrame, project.totalFrame),
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MonoLabel,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${project.renderEngine} · ${project.resolutionX}×${project.resolutionY} (${project.resolutionScale}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectThumbnail(project: ProjectInfo, dimmed: Boolean) {
    val lastFrame = if (project.finishedFrame > 0) {
        (project.finishedFrame - 1) * project.frameStep + project.frameStart
    } else null
    Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (lastFrame == null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = frameUrl(project, lastFrame, thumb = true),
                contentDescription = stringResource(R.string.project_thumbnail),
                filterQuality = FilterQuality.High,
                contentScale = ContentScale.Crop,
                colorFilter = if (dimmed) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.3f) })
                } else null,
                modifier = Modifier.fillMaxSize().alpha(if (dimmed) 0.75f else 1f),
            )
        }
    }
}

/** Frame image URL on the desktop app's HTTP server. */
internal fun frameUrl(project: ProjectInfo, frame: Int, thumb: Boolean): String =
    "http://${project.deviceIp}:28528/frame?id=${project.id}&frame=$frame&thumb=${if (thumb) 1 else 0}"
