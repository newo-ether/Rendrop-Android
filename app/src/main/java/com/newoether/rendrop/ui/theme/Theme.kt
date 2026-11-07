package com.newoether.rendrop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density

/** Mirrors AgoraTheme defaults: FOREST tonal-spot palette, Mi Outfit type, compact sp on narrow windows. */
@Composable
fun RendropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = remember(darkTheme) { rendropColorScheme(darkTheme) }
    val baseDensity = LocalDensity.current
    val windowWidthDp = with(baseDensity) { LocalWindowInfo.current.containerSize.width.toDp().value }
    // Zero before the first layout pass; keep the regular tier until a real width is known.
    val compact = windowWidthDp > 0f && windowWidthDp < COMPACT_WIDTH_DP
    val contentDensity = remember(baseDensity, compact) {
        if (compact) Density(baseDensity.density, baseDensity.fontScale * COMPACT_FONT_SCALE) else baseDensity
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography) {
        CompositionLocalProvider(LocalDensity provides contentDensity, content = content)
    }
}
