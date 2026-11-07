package com.newoether.rendrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// Agora dropdown geometry: 24dp menu corner, capsule item highlight inset 8dp from the sides.
private val MenuCorner = 24.dp
private val MenuItemInset = 8.dp
private val MenuItemPadding = PaddingValues(horizontal = 12.dp - MenuItemInset)

@Composable
fun RendropDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(MenuCorner),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 16.dp,
        content = content,
    )
}

@Composable
fun RendropDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        // Padding and clip precede the item's clickable so the ripple is inset and capsule-shaped.
        modifier = modifier
            .padding(horizontal = MenuItemInset)
            .clip(RoundedCornerShape(MenuCorner)),
        leadingIcon = leadingIcon,
        contentPadding = MenuItemPadding,
    )
}

/**
 * Agora's expressive pill tab switcher: the selected tab springs to a full pill, widens slightly
 * and fills with primary, while neighbours square off to an 8dp inner corner.
 */
@Composable
fun PillTabSwitcher(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
    tabHeight: Dp = if (icons != null) 56.dp else 44.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = selectedIndex == index
            val outer = tabHeight / 2f
            val inner = 8.dp
            val cornerSpec = spring<Dp>(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.01.dp
            )
            val start by animateDpAsState(if (isSelected || index == 0) outer else inner, cornerSpec, label = "tabStart")
            val end by animateDpAsState(
                if (isSelected || index == tabs.lastIndex) outer else inner, cornerSpec, label = "tabEnd"
            )
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                tween(220), label = "tabContainer"
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(220), label = "tabContent"
            )
            val weight by animateFloatAsState(
                if (isSelected) 1.13f else 1f,
                spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.001f
                ),
                label = "tabWeight"
            )
            val s = start.coerceIn(inner, outer)
            val e = end.coerceIn(inner, outer)
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier
                    .weight(weight)
                    .height(tabHeight)
                    .semantics { selected = isSelected; role = Role.Tab },
                shape = RoundedCornerShape(topStart = s, bottomStart = s, topEnd = e, bottomEnd = e),
                color = container,
                contentColor = content
            ) {
                val icon = icons?.getOrNull(index)
                if (icon != null) {
                    // Navigation-bar layout: icon stacked over a compact label.
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
