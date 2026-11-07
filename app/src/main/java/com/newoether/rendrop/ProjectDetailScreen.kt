package com.newoether.rendrop

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil3.compose.SubcomposeAsyncImage
import com.newoether.rendrop.ui.components.CollapsingTitleBar
import com.newoether.rendrop.ui.components.ContentInset
import com.newoether.rendrop.ui.components.ContentMaxWidth
import com.newoether.rendrop.ui.components.EmptyState
import com.newoether.rendrop.ui.components.GroupItem
import com.newoether.rendrop.ui.components.StatusPill
import com.newoether.rendrop.ui.components.TitleAreaHeight
import com.newoether.rendrop.ui.components.TitleBarHeight
import com.newoether.rendrop.ui.components.TitledPullRefresh
import com.newoether.rendrop.ui.components.rememberTitleFraction
import com.newoether.rendrop.ui.components.statusBarTop
import com.newoether.rendrop.ui.theme.MonoLabel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun ProjectDetailScreen(
    initialProject: ProjectInfo,
    onBack: () -> Unit,
) {
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var isAscending by rememberSaveable { mutableStateOf(false) }
    var sortVersion by rememberSaveable { mutableIntStateOf(0) }
    var selectedFrameNum by remember { mutableStateOf<Int?>(null) }
    var showVideoDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = context.projectRepository
    val repositoryState by repository.state.collectAsState()
    val project = repositoryState.projects.firstOrNull {
        it.deviceIp == initialProject.deviceIp && it.id == initialProject.id
    } ?: initialProject
    val workManager = WorkManager.getInstance(context)
    val workName = "video_${project.deviceIp}_${project.id}"
    val workInfos by workManager.getWorkInfosForUniqueWorkLiveData(workName).observeAsState()
    val isGenerating = workInfos?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true

    // key(sortVersion) gives each sort order its own scroll state, so the exiting and entering
    // lists in AnimatedContent never share one, and a new order starts at the top.
    val gridState = key(sortVersion) { rememberLazyGridState() }
    val listState = key(sortVersion) { rememberLazyListState() }
    val gridFraction by rememberTitleFraction(gridState)
    val listFraction by rememberTitleFraction(listState)

    val frameNumbers = remember(project.finishedFrame, project.frameStart, project.frameStep, isAscending) {
        val list = (1..project.finishedFrame).map { n -> project.frameStart + (n - 1) * project.frameStep }
        if (isAscending) list else list.reversed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TitledPullRefresh(
            refreshing = repositoryState.isRefreshing,
            onRefresh = { scope.launch { repository.refresh() } }
        ) {
            AnimatedContent(
                targetState = Triple(isGridView, isAscending, sortVersion),
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "ViewAndSortSwitch",
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = ContentMaxWidth)
            ) { (grid, _, _) ->
                val padding = PaddingValues(
                    start = ContentInset,
                    end = ContentInset,
                    top = statusBarTop() + TitleBarHeight,
                    bottom = 32.dp
                )
                if (grid) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        contentPadding = padding,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding()
                    ) {
                        item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                            DetailHeader(project, isEmpty = frameNumbers.isEmpty())
                        }
                        items(frameNumbers, key = { it }) { frameNum ->
                            FrameTile(project, frameNum) { selectedFrameNum = frameNum }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = padding,
                        modifier = Modifier.fillMaxSize().navigationBarsPadding()
                    ) {
                        item(key = "summary") {
                            DetailHeader(project, isEmpty = frameNumbers.isEmpty())
                        }
                        itemsIndexed(frameNumbers, key = { _, f -> f }) { index, frameNum ->
                            GroupItem(index, frameNumbers.size, onClick = { selectedFrameNum = frameNum }) {
                                FrameRow(project, frameNum)
                            }
                        }
                    }
                }
            }

            // Inside the refresh box so the indicator draws above the opaque title bar.
            CollapsingTitleBar(
                title = project.name,
                fraction = if (isGridView) gridFraction else listFraction,
                onBack = onBack,
                actions = {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { showVideoDialog = true }, enabled = !isGenerating && project.finishedFrame > 0) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = stringResource(R.string.generate_video),
                                tint = if (isGenerating) MaterialTheme.colorScheme.outline else LocalContentColor.current
                            )
                        }
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 2.dp)
                        }
                    }
                    IconButton(onClick = { isAscending = !isAscending; sortVersion++ }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort),
                            tint = if (isAscending) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(if (isGridView) R.string.list_view else R.string.grid_view)
                        )
                    }
                }
            )
        }

        val viewerFrameNumbers = remember(selectedFrameNum) { frameNumbers }
        // Same motion as Agora's media preview: the black backdrop only fades, the content fades
        // and scales on top of it, so the scaled content never exposes the screen behind it.
        AnimatedVisibility(
            visible = selectedFrameNum != null,
            enter = fadeIn(tween(durationMillis = 220)),
            exit = fadeOut(tween(durationMillis = 180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
        }
        AnimatedVisibility(
            visible = selectedFrameNum != null,
            enter = fadeIn(tween(durationMillis = 220)) +
                scaleIn(tween(durationMillis = 300, easing = FastOutSlowInEasing), initialScale = 0.96f),
            exit = fadeOut(tween(durationMillis = 180)) +
                scaleOut(tween(durationMillis = 220, easing = FastOutLinearInEasing), targetScale = 0.96f),
            modifier = Modifier.fillMaxSize()
        ) {
            // Keep the last frame for the duration of the exit animation.
            var lastFrameNum by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(selectedFrameNum) {
                if (selectedFrameNum != null) lastFrameNum = selectedFrameNum
            }
            val frameNum = lastFrameNum ?: return@AnimatedVisibility
            val initialIndex = remember(frameNum) { viewerFrameNumbers.indexOf(frameNum) }
            LightStatusBarIcons()
            if (initialIndex != -1) {
                FullScreenImageViewer(
                    project = project,
                    frameNumbers = viewerFrameNumbers,
                    initialIndex = initialIndex,
                    onDismiss = { selectedFrameNum = null }
                )
            }
        }
    }

    if (showVideoDialog) {
        val title = stringResource(R.string.generating_video)
        val startedText = stringResource(R.string.video_started)
        GenerateVideoDialog(
            onDismiss = { showVideoDialog = false },
            onGenerate = { quality, fps ->
                showVideoDialog = false
                enqueueVideo(context, workManager, workName, project, quality, fps, title, startedText)
            }
        )
    }

    BackHandler(enabled = selectedFrameNum != null) { selectedFrameNum = null }
    BackHandler(enabled = selectedFrameNum == null) { onBack() }
}

/** Forces light status-bar icons while the black frame viewer is visible. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onDispose { controller.isAppearanceLightStatusBars = previous }
        } else onDispose {}
    }
}

private fun enqueueVideo(
    context: Context,
    workManager: WorkManager,
    workName: String,
    project: ProjectInfo,
    quality: String,
    fps: Int,
    title: String,
    startedText: String,
) {
    // Instant notification so the user sees progress before the worker starts.
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notificationId = ("video_progress_" + project.deviceIp + "_" + project.id).hashCode()
    val notification = NotificationCompat.Builder(context, "video_generation")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("$title (0%)")
        .setContentText(startedText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(startedText))
        .setOngoing(true)
        .setSilent(true)
        .build()
    notificationManager.notify(notificationId, notification)

    val inputData = workDataOf(
        VideoWorkInput.PROJECT_NAME to project.name,
        VideoWorkInput.DEVICE_IP to project.deviceIp,
        VideoWorkInput.PROJECT_ID to project.id,
        VideoWorkInput.FRAME_START to project.frameStart,
        VideoWorkInput.FRAME_COUNT to project.finishedFrame,
        VideoWorkInput.FRAME_STEP to project.frameStep,
        VideoWorkInput.QUALITY to quality,
        VideoWorkInput.FPS to fps,
    )
    val request = OneTimeWorkRequestBuilder<VideoGeneratorWorker>()
        .setInputData(inputData)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
}

/** Title-area spacer plus a project summary card; the first item of both layouts. */
@Composable
private fun DetailHeader(project: ProjectInfo, isEmpty: Boolean) {
    val progress = if (project.totalFrame == 0) 0f else project.finishedFrame / project.totalFrame.toFloat()
    val (container, content, label) = projectStatusStyle(project.state)
    val isError = project.state.equals("error", ignoreCase = true)
    val barColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column {
        Spacer(Modifier.height(TitleAreaHeight))
        GroupItem(index = 0, count = 1) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(label, container, content)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.18f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.frames_finished, project.finishedFrame, project.totalFrame),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.frame_range, project.frameStart, project.frameEnd, project.frameStep),
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${project.renderEngine} · ${project.resolutionX}×${project.resolutionY} (${project.resolutionScale}%)",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isEmpty) {
            EmptyState(
                icon = Icons.Default.HourglassEmpty,
                message = stringResource(R.string.no_finished_frames),
                modifier = Modifier.height(280.dp)
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FrameImage(project: ProjectInfo, frameNum: Int, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = frameUrl(project, frameNum, thumb = true),
        contentDescription = stringResource(R.string.frame_label, frameNum),
        filterQuality = FilterQuality.High,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    )
}

@Composable
private fun FrameTile(project: ProjectInfo, frameNum: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        FrameImage(project, frameNum, Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
        Text(
            text = frameNum.toString(),
            style = MonoLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun FrameRow(project: ProjectInfo, frameNum: Int) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FrameImage(project, frameNum, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                stringResource(R.string.frame_label, frameNum),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${project.resolutionX}×${project.resolutionY}",
                style = MonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
