package com.newoether.rendrop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot

/** Agora's default FOREST seed; both apps share the same tonal palette. */
private const val SEED_FOREST = 0xFF2E7D32

fun rendropColorScheme(isDark: Boolean): ColorScheme =
    SchemeTonalSpot(Hct.fromInt(SEED_FOREST.toInt()), isDark, 0.0).toColorScheme()

private fun DynamicScheme.toColorScheme(): ColorScheme {
    val c = { argb: Int -> Color(argb) }
    return ColorScheme(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(secondary), onSecondary = c(onSecondary),
        secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary),
        tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        error = c(error), onError = c(onError),
        errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
        background = c(background), onBackground = c(onBackground),
        surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
        outline = c(outline), outlineVariant = c(outlineVariant),
        inversePrimary = c(inversePrimary),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
        surfaceTint = c(surfaceTint), scrim = c(scrim),
        surfaceDim = c(surfaceDim), surfaceBright = c(surfaceBright),
        surfaceContainerLowest = c(surfaceContainerLowest),
        surfaceContainerLow = c(surfaceContainerLow),
        surfaceContainer = c(surfaceContainer),
        surfaceContainerHigh = c(surfaceContainerHigh),
        surfaceContainerHighest = c(surfaceContainerHighest),
        primaryFixed = c(primaryFixed), primaryFixedDim = c(primaryFixedDim),
        onPrimaryFixed = c(onPrimaryFixed), onPrimaryFixedVariant = c(onPrimaryFixedVariant),
        secondaryFixed = c(secondaryFixed), secondaryFixedDim = c(secondaryFixedDim),
        onSecondaryFixed = c(onSecondaryFixed), onSecondaryFixedVariant = c(onSecondaryFixedVariant),
        tertiaryFixed = c(tertiaryFixed), tertiaryFixedDim = c(tertiaryFixedDim),
        onTertiaryFixed = c(onTertiaryFixed), onTertiaryFixedVariant = c(onTertiaryFixedVariant),
    )
}
