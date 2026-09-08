package com.hitrocker.game2048.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hitrocker.game2048.ui.theme.Tile1024
import com.hitrocker.game2048.ui.theme.Tile128
import com.hitrocker.game2048.ui.theme.Tile16
import com.hitrocker.game2048.ui.theme.Tile2
import com.hitrocker.game2048.ui.theme.Tile2048
import com.hitrocker.game2048.ui.theme.Tile256
import com.hitrocker.game2048.ui.theme.Tile32
import com.hitrocker.game2048.ui.theme.Tile4
import com.hitrocker.game2048.ui.theme.Tile512
import com.hitrocker.game2048.ui.theme.Tile64
import com.hitrocker.game2048.ui.theme.Tile8
import com.hitrocker.game2048.ui.theme.TileSuper
import com.hitrocker.game2048.ui.theme.TileTextDark
import com.hitrocker.game2048.ui.theme.TileTextLight

/**
 * Maps a tile's numeric value to its base hue (the classic 2048 palette). In the liquid-glass
 * theme these are used opaque only as the seed color for a translucent gradient in TileView, so the
 * frosted blur shows through.
 */
fun tileBackgroundColor(value: Int): Color = when (value) {
    2 -> Tile2
    4 -> Tile4
    8 -> Tile8
    16 -> Tile16
    32 -> Tile32
    64 -> Tile64
    128 -> Tile128
    256 -> Tile256
    512 -> Tile512
    1024 -> Tile1024
    2048 -> Tile2048
    else -> TileSuper // Any value beyond 2048, reached by continuing to play after winning.
}

/**
 * Now that the glass tiles are vibrant and mostly opaque, the lightest tiles (2 and 4, which use
 * pale beige hues) need dark text for contrast; every darker, higher-value tile reads best in light.
 */
fun tileTextColor(value: Int): Color = if (value <= 4) TileTextDark else TileTextLight

/**
 * Font size for a tile's number, scaled to the actual [cellSize] so the text fits on small cells
 * (6x6 / 5x7 boards) and stays proportional on large ones. Longer numbers get a smaller fraction
 * of the cell so values like 8192 still fit.
 */
fun tileFontSize(value: Int, cellSize: Dp): TextUnit {
    val fraction = when (value.toString().length) {
        1, 2 -> 0.42f
        3 -> 0.34f
        4 -> 0.28f
        else -> 0.22f
    }
    return (cellSize.value * fraction).sp
}
