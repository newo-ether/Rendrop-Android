package com.newoether.rendrop

import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.*
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    initialProject: ProjectInfo,
    onBack: () -> Unit,
    onProjectUpdate: (ProjectInfo) -> Unit = {}
) {
    var project by remember { mutableStateOf(initialProject) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var isAscending by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedFrameNum by remember { mutableStateOf<Int?>(null) }
    var showVideoDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workManager = WorkManager.getInstance(context)
    
    // Track video generation work
    val workInfos by workManager.getWorkInfosForUniqueWorkLiveData("video_${project.deviceIp}_${project.id}")
        .observeAsState()
    val isGenerating = workInfos?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val frameNumbers = remember(project.finishedFrame, isAscending) {
        val list = (1..project.finishedFrame).map { n ->
            project.frameStart + (n - 1) * project.frameStep
        }
        if (isAscending) list else list.reversed()
    }

    BackHandler {
        onBack()
    }

    LaunchedEffect(isAscending, isGridView) {
        if (isGridView) {
            if (gridState.layoutInfo.totalItemsCount > 0) gridState.animateScrollToItem(0)
        } else {
            if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(0)
        }
    }

    fun refreshProject() {
        scope.launch {
            refreshing = true
            try {
                val updated = withContext(Dispatchers.IO) {
                    fetchProjectDetail(project.deviceIp, project.id)
                }
                if (updated != null) {
                    project = updated
                    onProjectUpdate(updated)
                }
                delay(200)
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val updated = withContext(Dispatchers.IO) {
                fetchProjectDetail(project.deviceIp, project.id)
            }
            if (updated != null) {
                project = updated
                onProjectUpdate(updated)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.frames_finished, project.finishedFrame, project.totalFrame),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Video Generation Button
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { showVideoDialog = true },
                            enabled = !isGenerating && project.finishedFrame > 0
                        ) {
                            Icon(
                                Icons.Default.Movie, 
                                contentDescription = stringResource(R.string.generate_video),
                                tint = if (isGenerating) MaterialTheme.colorScheme.outline else LocalContentColor.current
                            )
                        }
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = { isAscending = !isAscending }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort),
                            tint = if (isAscending) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) stringResource(R.string.list_view) else stringResource(R.string.grid_view)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshProject() },
            state = rememberPullToRefreshState(),
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            if (project.finishedFrame == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_finished_frames), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                AnimatedContent(
                    targetState = isGridView to isAscending,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    label = "ViewAndSortSwitch"
                ) { (targetGridView, _) ->
                    if (targetGridView) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(frameNumbers, key = { it }) { frameNum ->
                                FrameIconItem(project, frameNum) {
                                    selectedFrameNum = frameNum
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(frameNumbers, key = { it }) { frameNum ->
                                FrameListItem(project, frameNum) {
                                    selectedFrameNum = frameNum
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVideoDialog) {
        GenerateVideoDialog(
            onDismiss = { showVideoDialog = false },
            onGenerate = { quality, fps ->
                showVideoDialog = false
                
                // Show instant notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channelId = "video_generation"
                val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(context.getString(R.string.generating_video) + " (0%)")
                    .setContentText(context.getString(R.string.video_started))
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
                notificationManager.notify(1001, notification)

                val inputData = workDataOf(
                    "projectName" to project.name,
                    "deviceIp" to project.deviceIp,
                    "projectId" to project.id,
                    "frameNumbers" to (1..project.finishedFrame).map { n ->
                        project.frameStart + (n - 1) * project.frameStep
                    }.toIntArray(),
                    "quality" to quality,
                    "fps" to fps
                )
                
                val workRequest = OneTimeWorkRequestBuilder<VideoGeneratorWorker>()
                    .setInputData(inputData)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                
                workManager.enqueueUniqueWork(
                    "video_${project.deviceIp}_${project.id}",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            }
        )
    }

    selectedFrameNum?.let { frameNum ->
        val initialIndex = frameNumbers.indexOf(frameNum)
        if (initialIndex != -1) {
            FullScreenImageViewer(
                project = project,
                frameNumbers = frameNumbers,
                initialIndex = initialIndex,
                onDismiss = { selectedFrameNum = null }
            )
        }
    }
}

@Composable
fun GenerateVideoDialog(
    onDismiss: () -> Unit,
    onGenerate: (String, Int) -> Unit
) {
    var quality by remember { mutableStateOf("low") }
    var fpsText by remember { mutableStateOf("30") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generate_video)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Quality Selection
                Column {
                    Text(stringResource(R.string.quality), style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedCard(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (quality == "low") stringResource(R.string.low_quality) else stringResource(R.string.high_quality),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.low_quality)) },
                                onClick = { quality = "low"; expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.high_quality)) },
                                onClick = { quality = "high"; expanded = false }
                            )
                        }
                    }
                }

                // FPS Input
                OutlinedTextField(
                    value = fpsText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) fpsText = it },
                    label = { Text(stringResource(R.string.frame_rate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(quality, fpsText.toIntOrNull() ?: 30) },
                enabled = fpsText.isNotEmpty()
            ) {
                Text(stringResource(R.string.generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun FrameIconItem(project: ProjectInfo, frameNum: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        SubcomposeAsyncImage(
            model = "http://${project.deviceIp}:28528/frame?id=${project.id}&frame=$frameNum&thumb=1",
            contentDescription = stringResource(R.string.frame_label, frameNum),
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        Text(
            text = stringResource(R.string.frame_label, frameNum),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun FrameListItem(project: ProjectInfo, frameNum: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = "http://${project.deviceIp}:28528/frame?id=${project.id}&frame=$frameNum&thumb=1",
                contentDescription = stringResource(R.string.frame_label, frameNum),
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.frame_label, frameNum),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${project.resolutionX}x${project.resolutionY}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FullScreenImageViewer(
    project: ProjectInfo,
    frameNumbers: List<Int>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { frameNumbers.size }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            var currentScale by remember { mutableFloatStateOf(1f) }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = currentScale <= 1.05f
            ) { page ->
                val frameNum = frameNumbers[page]
                ZoomableImageItem(
                    project = project,
                    frameNum = frameNum,
                    isPagerScrolling = pagerState.isScrollInProgress,
                    onScaleChanged = { if (page == pagerState.currentPage) currentScale = it }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${project.name} - " + stringResource(R.string.frame_label, frameNumbers[pagerState.currentPage]),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.shadow(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ZoomableImageItem(
    project: ProjectInfo,
    frameNum: Int,
    isPagerScrolling: Boolean,
    onScaleChanged: (Float) -> Unit
) {
    var scale by remember(frameNum) { mutableFloatStateOf(1f) }
    var offset by remember(frameNum) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isLoaded by remember(frameNum) { mutableStateOf(false) }

    LaunchedEffect(scale) {
        onScaleChanged(scale)
    }

    LaunchedEffect(isPagerScrolling) {
        if (isPagerScrolling && scale != 1f) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(isLoaded, frameNum) {
                if (!isLoaded) return@pointerInput
                
                awaitEachGesture {
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop

                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val dragAmount = pan.getDistance()
                                if (zoom != 1f || dragAmount > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val isMultiTouch = event.changes.size > 1
                                    val isHorizontalPan = abs(panChange.x) > abs(panChange.y)
                                    
                                    if (scale > 1f || isMultiTouch || !isHorizontalPan) {
                                        val oldScale = scale
                                        scale = (scale * zoomChange).coerceIn(1f, 5f)

                                        if (scale > 1f) {
                                            val imageAspectRatio = if (project.resolutionY > 0) project.resolutionX.toFloat() / project.resolutionY.toFloat() else 1f
                                            val containerAspectRatio = if (containerSize.height > 0) containerSize.width.toFloat() / containerSize.height.toFloat() else 1f
                                            val contentWidth: Float
                                            val contentHeight: Float
                                            if (imageAspectRatio > containerAspectRatio) {
                                                contentWidth = containerSize.width.toFloat()
                                                contentHeight = containerSize.width.toFloat() / imageAspectRatio
                                            } else {
                                                contentHeight = containerSize.height.toFloat()
                                                contentWidth = containerSize.height.toFloat() * imageAspectRatio
                                            }

                                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                            val r = scale / oldScale
                                            val newOffset = offset * r + (centroid - center) * (1f - r) + panChange
                                            
                                            val maxX = (contentWidth * scale - containerSize.width).coerceAtLeast(0f) / 2
                                            val maxY = (contentHeight * scale - containerSize.height).coerceAtLeast(0f) / 2
                                            
                                            offset = Offset(
                                                newOffset.x.coerceIn(-maxX, maxX),
                                                newOffset.y.coerceIn(-maxY, maxY)
                                            )
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            }
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data("http://${project.deviceIp}:28528/frame?id=${project.id}&frame=$frameNum&thumb=0")
                .size(coil3.size.Size.ORIGINAL)
                .build(),
            contentDescription = stringResource(R.string.frame_label, frameNum),
            onSuccess = { isLoaded = true },
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    clip = true
                ),
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        )
    }
}