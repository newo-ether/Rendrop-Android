package com.newoether.rendrop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/** One action revealed by [FabMenu]. */
data class FabMenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private val FabCollapsedWidth = 56.dp
private val FabItemStartPadding = 24.dp
private val FabItemIconSize = 24.dp
private val FabItemIconGap = 10.dp
private val FabItemEndPadding = 24.dp
private const val FabItemStagger = 80L

/**
 * Expandable FAB restored from the original Rendrop design: the main button springs in when
 * [visible], and tapping it staggers out extended action pills that grow from the FAB width.
 */
@Composable
fun FabMenu(visible: Boolean, actions: List<FabMenuAction>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val fabScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = if (visible) spring(dampingRatio = 0.5f, stiffness = 400f)
        else spring(dampingRatio = 2.5f, stiffness = 1500f),
        label = "fabScale"
    )
    val fabAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "fabAlpha")
    val fabRotation by animateFloatAsState(if (expanded) 45f else 0f, tween(300), label = "fabRotation")
    // All pills share one width fitted to the longest label, so they line up without a magic number.
    val labelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = actions.map { it.label }
    val expandedWidth = remember(labels, labelStyle, density) {
        val widestPx = labels.maxOfOrNull { textMeasurer.measure(it, labelStyle, maxLines = 1).size.width } ?: 0
        FabItemStartPadding + FabItemIconSize + FabItemIconGap + with(density) { widestPx.toDp() } + FabItemEndPadding
    }
    val widths = remember(actions.size) { List(actions.size) { Animatable(FabCollapsedWidth, Dp.VectorConverter) } }
    val alphas = remember(actions.size) { List(actions.size) { Animatable(0f) } }

    if (!visible) expanded = false

    LaunchedEffect(expanded, expandedWidth) {
        if (expanded) {
            // Bottom item first, so the menu unfolds upward from the FAB.
            actions.indices.reversed().forEachIndexed { order, i ->
                launch {
                    delay(order * FabItemStagger)
                    launch { widths[i].animateTo(expandedWidth, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                    launch { alphas[i].animateTo(1f, tween(280)) }
                }
            }
        } else {
            actions.indices.forEachIndexed { order, i ->
                launch {
                    delay(order * FabItemStagger)
                    launch { alphas[i].animateTo(0f, tween(200)) }
                    launch { widths[i].animateTo(FabCollapsedWidth, spring(dampingRatio = 1.5f, stiffness = 1500f)) }
                }
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            actions.forEachIndexed { i, action ->
                ExtendedFabItem(
                    text = action.label,
                    icon = action.icon,
                    width = widths[i].value,
                    alpha = alphas[i].value,
                    onClick = { expanded = false; action.onClick() }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (fabAlpha > 0f) {
            // Shadow fades faster than the button so it never lingers as a grey disc.
            val shadowColor = DefaultShadowColor.copy(alpha = fabAlpha.pow(8))
            Box(
                Modifier
                    .size(64.dp)
                    .scale(fabScale)
                    .shadow(6.dp, RoundedCornerShape(31.dp), clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            ) {
                FloatingActionButton(
                    onClick = { expanded = !expanded },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp).alpha(fabAlpha),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = actions.joinToString(" / ") { it.label },
                        modifier = Modifier.rotate(fabRotation)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtendedFabItem(
    text: String,
    icon: ImageVector,
    width: Dp,
    alpha: Float,
    onClick: () -> Unit,
    height: Dp = 56.dp,
) {
    if (alpha <= 0f) return
    val shadowColor = DefaultShadowColor.copy(alpha = alpha)
    val shape = RoundedCornerShape(height / 2)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(width, height)
            .shadow(8.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            .alpha(alpha),
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(FabItemStartPadding))
            Icon(icon, contentDescription = null, modifier = Modifier.size(FabItemIconSize))
            Spacer(Modifier.width(FabItemIconGap))
            Text(text, maxLines = 1, overflow = TextOverflow.Visible, style = MaterialTheme.typography.labelLarge)
        }
    }
}
