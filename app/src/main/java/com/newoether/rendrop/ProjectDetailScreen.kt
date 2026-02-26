package com.newoether.rendrop

import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.*
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    var sortVersion by rememberSaveable { mutableIntStateOf(0) }
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

    // Use key(sortVersion) to create a new state instance when sort order changes.
    // This prevents the "shared state" bug during AnimatedContent transition where the
    // exiting and entering lists fight over the same scroll state, causing stuck scrolling.
    // It also automatically resets the scroll position to top (0) for the new sort order.
    val gridState = key(sortVersion) { rememberLazyGridState() }
    val listState = key(sortVersion) { rememberLazyListState() }

    val frameNumbers = remember(project.finishedFrame, isAscending) {
        val list = (1..project.finishedFrame).map { n ->
            project.frameStart + (n - 1) * project.frameStep
        }
        if (isAscending) list else list.reversed()
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

    Box(modifier = Modifier.fillMaxSize()) {
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

                        IconButton(onClick = { 
                            isAscending = !isAscending 
                            sortVersion++
                        }) {
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
                        targetState = Triple(isGridView, isAscending, sortVersion),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "ViewAndSortSwitch"
                    ) { (targetGridView, _, _) ->
                        if (targetGridView) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(frameNumbers) { frameNum ->
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
                                items(frameNumbers) { frameNum ->
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

        val viewerFrameNumbers = remember(selectedFrameNum) {
            frameNumbers
        }

        AnimatedVisibility(
            visible = selectedFrameNum != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            // Keep the last frame num for the duration of the exit animation
            var lastFrameNum by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(selectedFrameNum) {
                if (selectedFrameNum != null) lastFrameNum = selectedFrameNum
            }

            val frameNum = lastFrameNum ?: return@AnimatedVisibility
            val initialIndex = remember(frameNum) { viewerFrameNumbers.indexOf(frameNum) }
            
            // Set status bar icons to light when viewer is active
            val view = androidx.compose.ui.platform.LocalView.current
            DisposableEffect(Unit) {
                val window = (view.context as? android.app.Activity)?.window
                if (window != null) {
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    val isAppearanceLightStatusBars = controller.isAppearanceLightStatusBars
                    controller.isAppearanceLightStatusBars = false
                    onDispose {
                        controller.isAppearanceLightStatusBars = isAppearanceLightStatusBars
                    }
                } else onDispose {}
            }

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
        val generatingVideoTitle = stringResource(R.string.generating_video)
        val videoStartedText = stringResource(R.string.video_started)
        
        GenerateVideoDialog(
            onDismiss = { showVideoDialog = false },
            onGenerate = { quality, fps ->
                showVideoDialog = false
                
                // Show instant notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channelId = "video_generation"
                val videoNotificationId = ("video_progress_" + project.deviceIp + "_" + project.id).hashCode()
                
                val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("$generatingVideoTitle (0%)")
                    .setContentText(videoStartedText)
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(videoStartedText))
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
                notificationManager.notify(videoNotificationId, notification)

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

    BackHandler(enabled = selectedFrameNum == null) {
        onBack()
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
    
    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Consume all touch events to prevent clicking things behind the viewer
                detectTapGestures { }
            }
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
                onScaleChanged = { if (page == pagerState.currentPage) currentScale = it },
                onDismiss = onDismiss
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.9f),
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${project.name} - " + stringResource(R.string.frame_label, frameNumbers[pagerState.currentPage]),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun ZoomableImageItem(
    project: ProjectInfo,
    frameNum: Int,
    isPagerScrolling: Boolean,
    onScaleChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    var scale by remember(frameNum) { mutableFloatStateOf(1f) }
    var offsetX by remember(frameNum) { mutableFloatStateOf(0f) }
    var offsetY by remember(frameNum) { mutableFloatStateOf(0f) }
    
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    var lastCentroid by remember { mutableStateOf(Offset.Unspecified) }
    var isLoaded by remember(frameNum) { mutableStateOf(false) }

    // Use project resolution as fallback image size
    LaunchedEffect(project) {
        if (imageSize == Size.Zero && project.resolutionX > 0 && project.resolutionY > 0) {
            imageSize = Size(project.resolutionX.toFloat(), project.resolutionY.toFloat())
        }
    }

    fun getMaxOffsets(currentScale: Float): Pair<Float, Float> {
        if (imageSize == Size.Zero || containerSize == Size.Zero) return 0f to 0f
        val imageAspectRatio = imageSize.width / imageSize.height
        val containerAspectRatio = containerSize.width / containerSize.height
        
        val contentWidth = if (imageAspectRatio > containerAspectRatio) containerSize.width else containerSize.height * imageAspectRatio
        val contentHeight = if (imageAspectRatio > containerAspectRatio) containerSize.width / imageAspectRatio else containerSize.height
        
        val maxX = (contentWidth * currentScale - containerSize.width).coerceAtLeast(0f) / 2f
        val maxY = (contentHeight * currentScale - containerSize.height).coerceAtLeast(0f) / 2f
        return maxX to maxY
    }

    // Helper for rubber-band resistance
    fun rubberBandValue(fullDelta: Float, dimension: Float): Float {
        if (dimension <= 0f) return 0f
        val c = 0.45f
        return (fullDelta * c * dimension) / (dimension + c * fullDelta)
    }

    LaunchedEffect(scale) {
        onScaleChanged(scale)
    }

    LaunchedEffect(isPagerScrolling) {
        if (isPagerScrolling && (scale != 1f || offsetX != 0f || offsetY != 0f)) {
            animationJob?.cancel()
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(frameNum) {
                detectTapGestures(
                    onTap = {
                        if (scale <= 1.05f) onDismiss()
                    },
                    onDoubleTap = { tapOffset ->
                        animationJob?.cancel()
                        animationJob = scope.launch {
                            val startScale = scale
                            val startOffsetX = offsetX
                            val startOffsetY = offsetY

                            if (startScale > 1.05f) {
                                // Zoom Out to 1x
                                val targetScale = 1f
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)

                                AnimationState(startScale).animateTo(
                                    targetScale,
                                    spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        visibilityThreshold = 0.001f
                                    )
                                ) {
                                    scale = value
                                    // Pivot zoom out: shrink towards the tapped point
                                    val r = if (startScale != 0f) value / startScale else 1f
                                    val unconstrainedX = startOffsetX * r + (tapOffset.x - center.x) * (1f - r)
                                    val unconstrainedY = startOffsetY * r + (tapOffset.y - center.y) * (1f - r)

                                    // Clamp to valid boundaries for the CURRENT scale
                                    val (maxX, maxY) = getMaxOffsets(value)
                                    offsetX = unconstrainedX.coerceIn(-maxX, maxX)
                                    offsetY = unconstrainedY.coerceIn(-maxY, maxY)
                                }
                            } else {
                                // Zoom In to 3x
                                val targetScale = 3f
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)

                                AnimationState(startScale).animateTo(
                                    targetScale,
                                    spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        visibilityThreshold = 0.001f
                                    )
                                ) {
                                    scale = value
                                    val r = if (startScale != 0f) value / startScale else 1f

                                    // Pivot zoom: maintain the tapped point visually stationary
                                    val unconstrainedX = startOffsetX * r + (tapOffset.x - center.x) * (1f - r)
                                    val unconstrainedY = startOffsetY * r + (tapOffset.y - center.y) * (1f - r)
                                    // Clamp to valid boundaries for the CURRENT scale
                                    val (maxX, maxY) = getMaxOffsets(value)
                                    offsetX = unconstrainedX.coerceIn(-maxX, maxX)
                                    offsetY = unconstrainedY.coerceIn(-maxY, maxY)
                                }
                            }
                        }
                    }
                )
            }
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data("http://${project.deviceIp}:28528/frame?id=${project.id}&frame=$frameNum&thumb=0")
                .size(coil3.size.Size.ORIGINAL)
                .build(),
            contentDescription = stringResource(R.string.frame_label, frameNum),
            onSuccess = { 
                isLoaded = true 
                imageSize = it.painter.intrinsicSize
            },
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(frameNum) {
                    val velocityTracker = VelocityTracker()
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        animationJob?.cancel()
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        // Reset centroid for new gesture
                        lastCentroid = Offset.Unspecified
                        // Maintain logical state for the duration of this gesture
                        var logicalScale = scale
                        var logicalOffsetX = offsetX
                        var logicalOffsetY = offsetY
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!pastTouchSlop) {
                                val panAmount = panChange.getDistance()
                                if (zoomChange != 1f || panAmount > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }
                            if (pastTouchSlop) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (zoomChange != 1f && centroid != Offset.Unspecified) {
                                    lastCentroid = centroid
                                }
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val oldVisualScale = scale
                                    // 1. Update logical scale and map to visual scale
                                    logicalScale = (logicalScale * zoomChange).coerceIn(0.1f, 30f)
                                    val newVisualScale = if (logicalScale < 1f) {
                                        1f - rubberBandValue(1f - logicalScale, 1f)
                                    } else if (logicalScale > 10f) {
                                        10f + rubberBandValue(logicalScale - 10f, 5f)
                                    } else {
                                        logicalScale
                                    }
                                    // 2. Use the visual scale ratio to transform offsets (keeps centroid stable)
                                    val r = if (oldVisualScale != 0f) newVisualScale / oldVisualScale else 1f
                                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    // Update logical offsets (used for rubber-band calculation)
                                    logicalOffsetX = logicalOffsetX * r + (centroid.x - center.x) * (1f - r) + panChange.x
                                    logicalOffsetY = logicalOffsetY * r + (centroid.y - center.y) * (1f - r) + panChange.y
                                    val (maxX, maxY) = getMaxOffsets(newVisualScale)
                                    scale = newVisualScale
                                    offsetX = if (logicalOffsetX > maxX) maxX + rubberBandValue(logicalOffsetX - maxX, containerSize.width)
                                    else if (logicalOffsetX < -maxX) -maxX - rubberBandValue(-maxX - logicalOffsetX, containerSize.width)
                                    else logicalOffsetX
                                    offsetY = if (logicalOffsetY > maxY) maxY + rubberBandValue(logicalOffsetY - maxY, containerSize.height)
                                    else if (logicalOffsetY < -maxY) -maxY - rubberBandValue(-maxY - logicalOffsetY, containerSize.height)
                                    else logicalOffsetY
                                    
                                    // Consume if zoomed in or zooming to prevent pager from scrolling
                                    if (newVisualScale > 1.05f || zoomChange != 1f || abs(panChange.y) > abs(panChange.x)) {
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            }
                            if (event.changes.size == 1) {
                                val change = event.changes.first()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            } else {
                                velocityTracker.resetTracking()
                            }
                        } while (event.changes.any { it.pressed })
                        // Release handler: Snap-back or Fling
                        val rawVelocity = velocityTracker.calculateVelocity()
                        val maxV = with(density) { 2500.dp.toPx() }
                        val velocity = Velocity(
                            x = if (rawVelocity.x.isNaN()) 0f else rawVelocity.x.coerceIn(-maxV, maxV),
                            y = if (rawVelocity.y.isNaN()) 0f else rawVelocity.y.coerceIn(-maxV, maxV)
                        )

                        animationJob = scope.launch {
                            val rbCoeff = 0.45f
                            if (scale < 0.95f || scale > 10.05f) {
                                val sS = scale
                                val sX = offsetX
                                val sY = offsetY
                                val targetS = scale.coerceIn(1f, 10f)
                                val (targetMaxX, targetMaxY) = getMaxOffsets(targetS)
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val pivot = if (lastCentroid != Offset.Unspecified) lastCentroid else center
                                val targetR = if (sS != 0f) targetS / sS else 1f
                                val finalPivotX = sX * targetR + (pivot.x - center.x) * (1f - targetR)
                                val finalPivotY = sY * targetR + (pivot.y - center.y) * (1f - targetR)
                                val targetX = finalPivotX.coerceIn(-targetMaxX, targetMaxX)
                                val targetY = finalPivotY.coerceIn(-targetMaxY, targetMaxY)
                                AnimationState(0f).animateTo(
                                    1f,
                                    spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
                                ) {
                                    val currentScale = sS + (targetS - sS) * value
                                    scale = currentScale
                                    val r = if (sS != 0f) currentScale / sS else 1f
                                    val pivotOffsetX = sX * r + (pivot.x - center.x) * (1f - r)
                                    val pivotOffsetY = sY * r + (pivot.y - center.y) * (1f - r)
                                    offsetX = pivotOffsetX + (targetX - finalPivotX) * value
                                    offsetY = pivotOffsetY + (targetY - finalPivotY) * value
                                }
                            } else {
                                // Handle axes independently
                                launch {
                                    val (maxX, _) = getMaxOffsets(scale)
                                    if (offsetX > maxX || offsetX < -maxX) {
                                        val targetX = offsetX.coerceIn(-maxX, maxX)
                                        // Use damped velocity for visual snap-back to avoid "kick"
                                        AnimationState(initialValue = offsetX, initialVelocity = velocity.x * rbCoeff)
                                            .animateTo(targetX, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                                                offsetX = value
                                            }
                                    } else if (velocity.x != 0f) {
                                        val decay = splineBasedDecay<Float>(density)
                                        var hitX = false
                                        var velX = 0f
                                        var posX = 0f
                                        AnimationState(initialValue = offsetX, initialVelocity = velocity.x)
                                            .animateDecay(decay) {
                                                val (curMaxX, _) = getMaxOffsets(scale)
                                                if (value > curMaxX || value < -curMaxX) {
                                                    velX = this.velocity
                                                    posX = value
                                                    hitX = true
                                                    cancelAnimation()
                                                } else {
                                                    offsetX = value
                                                }
                                            }
                                        if (hitX) {
                                            val (curMaxX, _) = getMaxOffsets(scale)
                                            val finalTargetX = posX.coerceIn(-curMaxX, curMaxX)
                                            AnimationState(initialValue = posX, initialVelocity = velX * rbCoeff)
                                                .animateTo(finalTargetX, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                                                    offsetX = value
                                                }
                                        }
                                    }
                                }
                                launch {
                                    val (_, maxY) = getMaxOffsets(scale)
                                    if (offsetY > maxY || offsetY < -maxY) {
                                        val targetY = offsetY.coerceIn(-maxY, maxY)
                                        AnimationState(initialValue = offsetY, initialVelocity = velocity.y * rbCoeff)
                                            .animateTo(targetY, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                                                offsetY = value
                                            }
                                    } else if (velocity.y != 0f) {
                                        val decay = splineBasedDecay<Float>(density)
                                        var hitY = false
                                        var velY = 0f
                                        var posY = 0f
                                        AnimationState(initialValue = offsetY, initialVelocity = velocity.y)
                                            .animateDecay(decay) {
                                                val (_, curMaxY) = getMaxOffsets(scale)
                                                if (value > curMaxY || value < -curMaxY) {
                                                    velY = this.velocity
                                                    posY = value
                                                    hitY = true
                                                    cancelAnimation()
                                                } else {
                                                    offsetY = value
                                                }
                                            }
                                        if (hitY) {
                                            val (_, curMaxY) = getMaxOffsets(scale)
                                            val finalTargetY = posY.coerceIn(-curMaxY, curMaxY)
                                            AnimationState(initialValue = posY, initialVelocity = velY * rbCoeff)
                                                .animateTo(finalTargetY, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                                                    offsetY = value
                                                }
                                        }
                                    }
                                }
                            }
                        }
                        velocityTracker.resetTracking()
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
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