package com.manish.demo.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Enum to represent different window size classes
 */
enum class WindowSize {
    COMPACT,  // Phones (< 600dp width)
    MEDIUM,   // Small tablets (600dp - 839dp width)
    EXPANDED  // Large tablets (>= 840dp width)
}

/**
 * Get the current window size based on screen width
 */
@Composable
fun getWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    return when {
        screenWidth < 600 -> WindowSize.COMPACT
        screenWidth < 840 -> WindowSize.MEDIUM
        else -> WindowSize.EXPANDED
    }
}

/**
 * Data class holding all responsive sizing values
 */
data class ResponsiveSizes(
    // Movie card dimensions
    val movieCardWidth: Dp,
    val movieCardHeight: Dp,
    val posterWidth: Dp,
    val posterHeight: Dp,

    // Text sizes
    val titleLargeSize: TextUnit,
    val titleSize: TextUnit,
    val subtitleSize: TextUnit,
    val bodySize: TextUnit,
    val captionSize: TextUnit,
    val smallSize: TextUnit,

    // Spacing
    val paddingLarge: Dp,
    val paddingMedium: Dp,
    val paddingSmall: Dp,
    val paddingTiny: Dp,

    // Icon sizes
    val iconLarge: Dp,
    val iconMedium: Dp,
    val iconSmall: Dp,

    // Grid columns
    val gridColumns: Int
)

/**
 * Get responsive sizes based on current window size
 */
@Composable
fun getResponsiveSizes(): ResponsiveSizes {
    val windowSize = getWindowSize()

    return when (windowSize) {
        WindowSize.COMPACT -> ResponsiveSizes(
            movieCardWidth = 140.dp,
            movieCardHeight = 200.dp,
            posterWidth = 100.dp,
            posterHeight = 140.dp,
            titleLargeSize = 28.sp,
            titleSize = 20.sp,
            subtitleSize = 16.sp,
            bodySize = 14.sp,
            captionSize = 12.sp,
            smallSize = 10.sp,
            paddingLarge = 24.dp,
            paddingMedium = 16.dp,
            paddingSmall = 8.dp,
            paddingTiny = 4.dp,
            iconLarge = 48.dp,
            iconMedium = 24.dp,
            iconSmall = 16.dp,
            gridColumns = 2
        )
        WindowSize.MEDIUM -> ResponsiveSizes(
            movieCardWidth = 180.dp,
            movieCardHeight = 260.dp,
            posterWidth = 130.dp,
            posterHeight = 180.dp,
            titleLargeSize = 36.sp,
            titleSize = 24.sp,
            subtitleSize = 20.sp,
            bodySize = 16.sp,
            captionSize = 14.sp,
            smallSize = 12.sp,
            paddingLarge = 32.dp,
            paddingMedium = 24.dp,
            paddingSmall = 12.dp,
            paddingTiny = 6.dp,
            iconLarge = 64.dp,
            iconMedium = 32.dp,
            iconSmall = 20.dp,
            gridColumns = 3
        )
        WindowSize.EXPANDED -> ResponsiveSizes(
            movieCardWidth = 220.dp,
            movieCardHeight = 310.dp,
            posterWidth = 160.dp,
            posterHeight = 220.dp,
            titleLargeSize = 42.sp,
            titleSize = 28.sp,
            subtitleSize = 22.sp,
            bodySize = 18.sp,
            captionSize = 16.sp,
            smallSize = 14.sp,
            paddingLarge = 40.dp,
            paddingMedium = 32.dp,
            paddingSmall = 16.dp,
            paddingTiny = 8.dp,
            iconLarge = 72.dp,
            iconMedium = 40.dp,
            iconSmall = 24.dp,
            gridColumns = 4
        )
    }
}

/**
 * Responsive utility for getting adaptive minimum width for grids
 */
@Composable
fun getAdaptiveMinWidth(): Dp {
    val sizes = getResponsiveSizes()
    return sizes.movieCardWidth
}

