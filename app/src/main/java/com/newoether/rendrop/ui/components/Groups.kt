package com.newoether.rendrop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newoether.rendrop.R

// Agora's grouped-list geometry: 24dp outer corners, 5dp inner corners, 2dp gap between rows.
internal val GroupOuterCorner = 24.dp
internal val GroupInnerCorner = 5.dp
internal val GroupItemGap = 2.dp

/** Shape for row [index] of a group with [count] rows. */
fun groupItemShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(GroupOuterCorner)
    index == 0 -> RoundedCornerShape(
        topStart = GroupOuterCorner, topEnd = GroupOuterCorner,
        bottomStart = GroupInnerCorner, bottomEnd = GroupInnerCorner
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = GroupInnerCorner, topEnd = GroupInnerCorner,
        bottomStart = GroupOuterCorner, bottomEnd = GroupOuterCorner
    )
    else -> RoundedCornerShape(GroupInnerCorner)
}

/** Primary-tinted group caption, aligned with the row content inset. */
@Composable
fun GroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = com.newoether.rendrop.ui.theme.MonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One row container inside a group; call with its position so corners match Agora. */
@Composable
fun GroupItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = groupItemShape(index, count)
    val padded = modifier
        .fillMaxWidth()
        .padding(top = if (index > 0) GroupItemGap else 0.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = padded,
            content = content
        )
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = padded,
            content = content
        )
    }
}

/** Agora SettingsItem: Medium bodyLarge headline, bodyMedium supporting text, optional slots. */
@Composable
fun ListRow(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val vertical = if (supporting == null) 12.dp else 16.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp, top = vertical, bottom = vertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                leading()
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) { headline() }
            if (supporting != null) {
                Spacer(Modifier.padding(top = 3.dp))
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) { supporting() }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Centered primary action row, the Agora "add" affordance at the end of a group. */
@Composable
fun ActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Capsule status badge. */
@Composable
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** Centered icon + message for empty lists. */
@Composable
fun EmptyState(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.padding(top = 12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Agora's circular back affordance. */
@Composable
fun CircularBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
    }
}
