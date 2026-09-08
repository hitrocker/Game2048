package com.hitrocker.game2048.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hitrocker.game2048.game.Tile

private const val TILE_SPACING_DP = 6
private const val TILE_CORNER_DP = 6

// Peak scale during a merge.
private const val MERGE_PEAK_SCALE = 1.18f

/**
 * Renders a single classic flat 2048 tile and drives its animations:
 * 1. **Slide** - the x/y offset springs toward the tile's [Tile.row]/[Tile.col] cell, so moves look
 *    like tiles gliding rather than teleporting.
 * 2. **Spawn pop** - brand-new tiles ([Tile.isNew]) spring in from 0 scale.
 * 3. **Merge pulse** - merged tiles ([Tile.isMerged]) briefly overshoot then settle.
 *
 * This composable owns layout, motion, and the flat visual (solid color + number). The caller wraps
 * each TileView in `key(tile.id)` (see [GameBoard]) so Compose preserves identity and the offset
 * animation interpolates.
 */
@Composable
fun TileView(
    tile: Tile,
    cellSize: Dp,
    animate: Boolean = true
) {
    // When animations are off, tiles snap straight to their cell (no slide). The animated value is
    // still declared but updated instantly, so toggling the setting works without rebuilding state.
    val animatedX by animateDpAsState(
        targetValue = cellSize * tile.col,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tileOffsetX"
    )
    val animatedY by animateDpAsState(
        targetValue = cellSize * tile.row,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tileOffsetY"
    )
    val offsetX = if (animate) animatedX else cellSize * tile.col
    val offsetY = if (animate) animatedY else cellSize * tile.row

    val scale = remember { Animatable(if (tile.isNew && animate) 0f else 1f) }

    // Re-runs whenever this tile's lifecycle flags (or the animate setting) change, so the pop/pulse
    // plays once per spawn or merge - and is skipped entirely when animations are disabled.
    LaunchedEffect(tile.id, tile.isNew, tile.isMerged, animate) {
        if (!animate) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        when {
            tile.isNew -> {
                scale.snapTo(0f)
                scale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
            tile.isMerged -> {
                scale.snapTo(1f)
                scale.animateTo(
                    MERGE_PEAK_SCALE,
                    spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh)
                )
                scale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
            else -> scale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(cellSize)
            .padding(TILE_SPACING_DP.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(TILE_CORNER_DP.dp))
            .background(tileBackgroundColor(tile.value)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tile.value.toString(),
            color = tileTextColor(tile.value),
            fontWeight = FontWeight.Bold,
            fontSize = tileFontSize(tile.value, cellSize)
        )
    }
}
