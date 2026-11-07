package com.newoether.rendrop.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Collapsing large-title geometry, ported from Agora's settings scaffold so the morph matches.
internal val TitleBarHeight = 64.dp
internal val TitleDockTop = 18.dp
internal val TitleBottomInset = 70.dp
internal val TitleAreaHeight = 90.dp
internal val TitleExpandedFont = 33.sp
internal val TitleCollapsedFont = 22.sp
internal val ContentMaxWidth = 840.dp
internal val ContentInset = 24.dp

private val TitleEasing = CubicBezierEasing(0.2f, 0f, 0.5f, 1f)

/** Distance the docked title rises through while the header scrolls away. */
internal val TitleTravel: Dp get() = TitleBarHeight + TitleAreaHeight - TitleBottomInset - TitleDockTop

@Composable
fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** Collapse fraction (0 expanded, 1 docked) for a list whose first item is the title-area spacer. */
@Composable
fun rememberTitleFraction(state: LazyListState): State<Float> {
    val travelPx = with(LocalDensity.current) { TitleTravel.toPx() }
    return remember(state, travelPx) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / travelPx).coerceIn(0f, 1f)
        }
    }
}

/** Grid variant of [rememberTitleFraction]; the first grid item must span the full line. */
@Composable
fun rememberTitleFraction(state: LazyGridState): State<Float> {
    val travelPx = with(LocalDensity.current) { TitleTravel.toPx() }
    return remember(state, travelPx) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / travelPx).coerceIn(0f, 1f)
        }
    }
}

/**
 * iOS-style collapsing title overlay. The title rises 1:1 with the scrolling header while its
 * scale and horizontal tuck follow an eased curve. [onBack] adds Agora's circular back button.
 */
@Composable
fun CollapsingTitleBar(
    title: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val top = statusBarTop()

    Box(modifier = modifier.fillMaxWidth()) {
        // Opaque bar hides list content scrolling underneath, including the status-bar strip.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(top + TitleBarHeight)
                .background(MaterialTheme.colorScheme.background)
        )
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth(),
        ) {
            val availableWidth = (maxWidth - ContentInset - 16.dp).coerceAtLeast(0.dp)
            // Shrink the expanded font until long titles fit on one line, down to a 20sp floor.
            val expandedFont = remember(title, availableWidth) {
                val floor = 20f
                val availablePx = with(density) { availableWidth.toPx() }
                var size = TitleExpandedFont.value
                while (size > floor) {
                    val measured = textMeasurer.measure(
                        title,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = size.sp)
                    ).size.width
                    if (measured <= availablePx) break
                    size -= 1f
                }
                size.coerceAtLeast(floor).sp
            }
            val scaleEnd = TitleCollapsedFont.value / expandedFont.value
            val eased = TitleEasing.transform(fraction)
            val titleScale = 1f - (1f - scaleEnd) * eased
            val expandedY = top + TitleBarHeight + TitleAreaHeight - TitleBottomInset
            val titleY = expandedY - TitleTravel * fraction
            val titleX = if (onBack != null) ContentInset + (70.dp - ContentInset) * eased else ContentInset

            if (onBack != null) {
                CircularBackButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 16.dp, top = top + 12.dp)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = top + 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = expandedFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .widthIn(max = availableWidth)
                    .offset(x = titleX, y = titleY)
                    .graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 0f)
                    }
            )
        }
    }
}

/** Pull-to-refresh whose indicator drops from below the status bar, above the title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitledPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val top = statusBarTop()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
            )
        },
        content = content,
    )
}
