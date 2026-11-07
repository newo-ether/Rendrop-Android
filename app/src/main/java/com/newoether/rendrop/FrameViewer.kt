package com.newoether.rendrop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.newoether.rendrop.ui.theme.MonoLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun FullScreenImageViewer(
    project: ProjectInfo,
    frameNumbers: List<Int>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { frameNumbers.size }

    // No background here: the caller draws the black backdrop as a separate fade-only layer so it
    // does not shrink with the scale transition.
    Box(
        modifier = Modifier
            .fillMaxSize()
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
            userScrollEnabled = currentScale <= PAGER_SCALE_LIMIT
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
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                ) {
                    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = project.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.frame_label, frameNumbers[pagerState.currentPage]),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MonoLabel
                    )
                }
            }
        }
    }
}

private enum class FrameLoadState { LOADING, LOADED, FAILED }

private enum class GestureOwner { UNDECIDED, ITEM, PAGER }

/** Above this zoom the pager is off and every drag pans the image. */
private const val PAGER_SCALE_LIMIT = 1.05f

/**
 * Zoom/pan frame page ported from Agora's ZoomableImageItem: raw scale maps through rubber-band
 * limits (1x..10x), offsets clamp hard to the content bounds, release either snaps scale back or
 * flings each axis with decay. Gestures stay disabled until the bitmap has loaded, and the loading
 * and error states draw outside the transformed layer so they never zoom.
 */
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
    var imageSize by remember(frameNum) { mutableStateOf(Size.Zero) }
    var loadState by remember(frameNum) { mutableStateOf(FrameLoadState.LOADING) }
    var animationJob by remember(frameNum) { mutableStateOf<Job?>(null) }
    val loaded = loadState == FrameLoadState.LOADED
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Maps "fit between the system bars" to user scale 1x.
    val baseScale = remember(containerSize, imageSize, statusBarHeight, navBarHeight) {
        if (containerSize == Size.Zero || imageSize == Size.Zero) 1f
        else {
            val imageAspect = imageSize.width / imageSize.height
            val containerAspect = containerSize.width / containerSize.height
            val fittedWidth = if (imageAspect > containerAspect) containerSize.width else containerSize.height * imageAspect
            val fittedHeight = if (imageAspect > containerAspect) containerSize.width / imageAspect else containerSize.height
            val barsPx = with(density) { statusBarHeight.toPx() + navBarHeight.toPx() }
            val sHeight = if (fittedHeight > 0f) (containerSize.height - barsPx) / fittedHeight else 1f
            val sWidth = if (fittedWidth > 0f) containerSize.width / fittedWidth else 1f
            minOf(sHeight, sWidth).coerceIn(0.1f, 1f)
        }
    }

    fun rubberBandValue(fullDelta: Float, dimension: Float): Float {
        if (dimension <= 0f) return 0f
        val c = 0.4f
        return (fullDelta * c * dimension) / (dimension + c * fullDelta)
    }

    fun visualScale(raw: Float): Float = when {
        raw < 1f -> 1f - rubberBandValue(1f - raw, 1f)
        raw > 10f -> 10f + rubberBandValue(raw - 10f, 5f)
        else -> raw
    }

    fun getMaxOffsets(currentRaw: Float): Pair<Float, Float> {
        if (imageSize == Size.Zero || containerSize == Size.Zero) return 0f to 0f
        val effectiveScale = visualScale(currentRaw) * baseScale
        val imageAspect = imageSize.width / imageSize.height
        val containerAspect = containerSize.width / containerSize.height
        val contentWidth = if (imageAspect > containerAspect) containerSize.width else containerSize.height * imageAspect
        val contentHeight = if (imageAspect > containerAspect) containerSize.width / imageAspect else containerSize.height
        val maxX = (contentWidth * effectiveScale - containerSize.width).coerceAtLeast(0f) / 2f
        val maxY = (contentHeight * effectiveScale - containerSize.height).coerceAtLeast(0f) / 2f
        return maxX to maxY
    }

    LaunchedEffect(frameNum) {
        snapshotFlow { visualScale(scale) }.collect { onScaleChanged(it) }
    }

    // A page swiped away resets so it is back at 1x when it returns.
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
            .pointerInput(frameNum, loaded) {
                detectTapGestures(
                    onTap = { if (visualScale(scale) <= 1.05f) onDismiss() },
                    onDoubleTap = { tapOffset ->
                        if (!loaded) return@detectTapGestures
                        animationJob?.cancel()
                        animationJob = scope.launch {
                            val s0 = scale
                            val tapRelX = tapOffset.x - containerSize.width / 2f
                            val tapRelY = tapOffset.y - containerSize.height / 2f
                            val isZoomIn = s0 <= 1.05f
                            val targetScale = if (isZoomIn) 3f else 1f
                            var prevS = s0
                            var prevOX = offsetX
                            var prevOY = offsetY
                            AnimationState(s0).animateTo(
                                targetScale,
                                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, 0.001f)
                            ) {
                                scale = value
                                val r = visualScale(value) / visualScale(prevS)
                                val (maxX, maxY) = getMaxOffsets(value)
                                if (isZoomIn) {
                                    // Pivot only along axes that will overflow, so a fitted axis stays centered.
                                    val isLandscape = imageSize.width / imageSize.height > containerSize.width / containerSize.height
                                    val longO = if (isLandscape) maxX > 0f else maxY > 0f
                                    val shortO = if (isLandscape) maxY > 0f else maxX > 0f
                                    val pivX = if (shortO || (longO && isLandscape)) tapRelX else 0f
                                    val pivY = if (shortO || (longO && !isLandscape)) tapRelY else 0f
                                    offsetX = prevOX * r + pivX * (1f - r)
                                    offsetY = prevOY * r + pivY * (1f - r)
                                } else {
                                    offsetX = (prevOX * r + tapRelX * (1f - r)).coerceIn(-maxX, maxX)
                                    offsetY = (prevOY * r + tapRelY * (1f - r)).coerceIn(-maxY, maxY)
                                }
                                prevS = value
                                prevOX = offsetX
                                prevOY = offsetY
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(frameUrl(project, frameNum, thumb = false))
                .size(coil3.size.Size.ORIGINAL)
                .build(),
            contentDescription = stringResource(R.string.frame_label, frameNum),
            onLoading = { loadState = FrameLoadState.LOADING },
            onSuccess = {
                imageSize = it.painter.intrinsicSize
                loadState = FrameLoadState.LOADED
            },
            onError = { loadState = FrameLoadState.FAILED },
            filterQuality = FilterQuality.High,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(frameNum, loaded) {
                    if (!loaded) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        animationJob?.cancel()
                        val touchSlop = viewConfiguration.touchSlop
                        var rawScale = scale
                        var logicalOffsetX = offsetX
                        var logicalOffsetY = offsetY
                        // The gesture is claimed once, when it crosses the slop: either the pager gets
                        // all of it (horizontal drag at 1x) or this item does. Deciding per event let
                        // the two handlers trade a single drag back and forth.
                        // Zoomed in: no page swipe is possible, so the item owns the gesture from
                        // the first event and nothing waits on the slop.
                        var owner = if (visualScale(scale) > PAGER_SCALE_LIMIT) GestureOwner.ITEM else GestureOwner.UNDECIDED
                        var slopPan = Offset.Zero
                        // Fling velocity comes from the accumulated pan, the signal that moves the
                        // image. Compose's VelocityTracker returned reversed, clamped velocities for
                        // short fast flicks here (3-4 samples), which threw the image backwards.
                        val panTracker = PanVelocityTracker()
                        panTracker.add(down.uptimeMillis, Offset.Zero)
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (owner == GestureOwner.UNDECIDED) {
                                slopPan += panChange
                                val multiTouch = event.changes.count { it.pressed } > 1
                                if (zoomChange != 1f || multiTouch) {
                                    owner = GestureOwner.ITEM
                                } else if (slopPan.getDistance() > touchSlop) {
                                    val atFit = visualScale(scale) <= PAGER_SCALE_LIMIT
                                    owner = if (atFit && abs(slopPan.x) > abs(slopPan.y)) GestureOwner.PAGER else GestureOwner.ITEM
                                }
                                if (owner == GestureOwner.PAGER) break
                            }
                            if (owner == GestureOwner.ITEM && (zoomChange != 1f || panChange != Offset.Zero)) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val oldVis = visualScale(scale)
                                rawScale = (rawScale * zoomChange).coerceIn(0.1f, 30f)
                                val newVis = visualScale(rawScale)
                                val r = if (oldVis != 0f) newVis / oldVis else 1f
                                val centerX = containerSize.width / 2f
                                val centerY = containerSize.height / 2f
                                if (abs(1f - r) > 0.0001f) {
                                    logicalOffsetX = logicalOffsetX * r + (centroid.x - centerX) * (1f - r)
                                    logicalOffsetY = logicalOffsetY * r + (centroid.y - centerY) * (1f - r)
                                } else {
                                    logicalOffsetX += panChange.x
                                    logicalOffsetY += panChange.y
                                }
                                val (maxX, maxY) = getMaxOffsets(rawScale)
                                scale = rawScale
                                offsetX = logicalOffsetX.coerceIn(-maxX, maxX)
                                offsetY = logicalOffsetY.coerceIn(-maxY, maxY)
                                logicalOffsetX = offsetX
                                logicalOffsetY = offsetY
                                // Once claimed, the whole gesture stays here; the pager never sees it.
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            val t = event.changes.first().uptimeMillis
                            if (event.changes.size == 1) panTracker.add(t, panChange) else panTracker.reset(t)
                        } while (event.changes.any { it.pressed })
                        // A tap or a drag handed to the pager leaves the item where it is.
                        if (owner != GestureOwner.ITEM) return@awaitEachGesture
                        val maxV = with(density) { 2500.dp.toPx() }
                        val rawVelocity = panTracker.velocity()
                        val velocity = Velocity(
                            x = rawVelocity.x.coerceIn(-maxV, maxV),
                            y = rawVelocity.y.coerceIn(-maxV, maxV)
                        )
                        animationJob = scope.launch {
                            if (scale < 0.95f || scale > 10.05f) {
                                // Snap back from rubber-banded scale around the center.
                                val sS = scale
                                val sX = offsetX
                                val sY = offsetY
                                val targetS = scale.coerceIn(1f, 10f)
                                val (targetMaxX, targetMaxY) = getMaxOffsets(targetS)
                                val targetR = if (sS != 0f) targetS / sS else 1f
                                val finalX = sX * targetR
                                val finalY = sY * targetR
                                val targetX = finalX.coerceIn(-targetMaxX, targetMaxX)
                                val targetY = finalY.coerceIn(-targetMaxY, targetMaxY)
                                AnimationState(0f).animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)) {
                                    val currentS = sS + (targetS - sS) * value
                                    scale = currentS
                                    val r = if (sS != 0f) visualScale(currentS) / visualScale(sS) else 1f
                                    offsetX = sX * r + (targetX - finalX) * value
                                    offsetY = sY * r + (targetY - finalY) * value
                                }
                            } else {
                                launch { flingAxis(offsetX, velocity.x, density, { getMaxOffsets(scale).first }) { offsetX = it } }
                                launch { flingAxis(offsetY, velocity.y, density, { getMaxOffsets(scale).second }) { offsetY = it } }
                            }
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = visualScale(scale) * baseScale,
                    scaleY = visualScale(scale) * baseScale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
        Crossfade(targetState = loadState, animationSpec = tween(180), label = "frameLoadState") { state ->
            when (state) {
                FrameLoadState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                FrameLoadState.LOADED -> Spacer(Modifier.fillMaxSize())
                FrameLoadState.FAILED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }
    }
}

/**
 * Release velocity from pan deltas: displacement over the last [WINDOW_MS] of movement, zero when
 * the finger rested longer than [REST_MS] before lifting.
 */
internal class PanVelocityTracker {
    private val times = ArrayList<Long>()
    private val positions = ArrayList<Offset>()
    private var total = Offset.Zero
    private var lastMoveTime = 0L
    private var lastTime = 0L

    fun add(time: Long, delta: Offset) {
        total += delta
        lastTime = time
        if (delta != Offset.Zero || times.isEmpty()) {
            times += time
            positions += total
            lastMoveTime = time
        }
    }

    /** Multi-touch restarts tracking so a pinch never feeds the fling. */
    fun reset(time: Long) {
        times.clear()
        positions.clear()
        total = Offset.Zero
        times += time
        positions += total
        lastMoveTime = time
        lastTime = time
    }

    fun velocity(): Offset {
        if (times.size < 2 || lastTime - lastMoveTime > REST_MS) return Offset.Zero
        val end = times.size - 1
        var start = end
        while (start > 0 && times[end] - times[start - 1] <= WINDOW_MS) start--
        if (start == end) start = end - 1
        val dt = (times[end] - times[start]).toFloat()
        if (dt <= 0f) return Offset.Zero
        return (positions[end] - positions[start]) * (1000f / dt)
    }

    private companion object {
        const val WINDOW_MS = 80L
        const val REST_MS = 50L
    }
}

/** Flings one pan axis with decay and stops hard at the content edge, as Agora does. */
private suspend fun flingAxis(
    start: Float,
    velocity: Float,
    density: androidx.compose.ui.unit.Density,
    maxOf: () -> Float,
    set: (Float) -> Unit,
) {
    val max = maxOf()
    if (start > max || start < -max) {
        set(start.coerceIn(-max, max))
        return
    }
    if (velocity == 0f) return
    AnimationState(start, velocity).animateDecay(splineBasedDecay(density)) {
        val curMax = maxOf()
        if (value > curMax || value < -curMax) {
            set(value.coerceIn(-curMax, curMax))
            cancelAnimation()
        } else {
            set(value)
        }
    }
}
