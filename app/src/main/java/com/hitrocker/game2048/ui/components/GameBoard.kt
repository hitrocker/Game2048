package com.hitrocker.game2048.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import com.hitrocker.game2048.game.MoveDirection
import com.hitrocker.game2048.game.Tile
import com.hitrocker.game2048.ui.theme.BoardBackground
import com.hitrocker.game2048.ui.theme.EmptyCell
import kotlin.math.abs

private const val TILE_SPACING_DP = 6

/** Minimum drag distance (in px) before a gesture counts as an intentional swipe. */
private const val SWIPE_THRESHOLD_PX = 40f

/**
 * How close to any screen edge a swipe may start before it's ignored. This keeps gestures that
 * begin in the system's back-gesture (left/right) and navigation-bar (bottom) zones from being
 * mistaken for board moves, so reaching for the nav bar no longer shifts the tiles.
 */
private const val EDGE_INSET_DP = 24

/**
 * Renders a [rows] x [cols] board: a static grid of empty cell backgrounds, plus a layer of live
 * [Tile]s on top. Supports any board shape (square or rectangle) via the [aspectRatio] derived from
 * the dimensions, with square cells throughout.
 *
 * Swipe handling is intentionally NOT here - it lives at the screen level (see GameScreen) so the
 * player can swipe anywhere, not just on the board. This composable is purely visual.
 *
 * Tiles are positioned with absolute, animated offsets (see [TileView]) rather than laid out by
 * a grid container, which is what lets them visually slide between cells instead of jumping.
 */
@Composable
fun GameBoard(
    tiles: List<Tile>,
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    mergingTiles: List<Tile> = emptyList()
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(cols.toFloat() / rows.toFloat())
            .clip(RoundedCornerShape(8.dp))
            .background(BoardBackground)
    ) {
        val cellSize = maxWidth / cols

        // Static background: one empty "well" per cell, regardless of whether it's occupied.
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                Box(
                    modifier = Modifier
                        .offset(x = cellSize * col, y = cellSize * row)
                        .size(cellSize)
                        .padding(TILE_SPACING_DP.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmptyCell)
                )
            }
        }

        // Live tiles. Each is wrapped in key(tile.id) so Compose preserves its identity (and
        // therefore its in-flight animation state) across recompositions triggered by moves.
        //
        // Merging (absorbed) tiles are emitted first so they render *beneath* the surviving tiles,
        // and in the SAME keyed loop as the board tiles: a tile that was a board tile last frame and
        // is an absorbed tile this frame keeps its identity by id, so its offset animates smoothly
        // from its old cell into the merge cell instead of snapping.
        (mergingTiles + tiles).forEach { tile ->
            key(tile.id) {
                TileView(tile = tile, cellSize = cellSize, animate = animate)
            }
        }
    }
}

/**
 * Accumulates drag deltas for a single gesture and, once the finger lifts, reports the
 * dominant direction as a single [MoveDirection] - provided the total drag exceeded
 * [SWIPE_THRESHOLD_PX], so small accidental touches are ignored.
 *
 * Public so the screen-level container (GameScreen) can install it via `Modifier.pointerInput`,
 * giving swipe-anywhere behavior.
 */
suspend fun PointerInputScope.detectSwipeGesture(onSwipe: (MoveDirection) -> Unit) {
    var dragOffset = Offset.Zero
    var startedAtEdge = false
    val edgeInset = EDGE_INSET_DP.dp.toPx()
    detectDragGestures(
        onDragStart = { start ->
            dragOffset = Offset.Zero
            // Drop gestures that begin in the edge margins (where the OS owns back / nav-bar swipes).
            startedAtEdge = start.x < edgeInset ||
                start.x > size.width - edgeInset ||
                start.y < edgeInset ||
                start.y > size.height - edgeInset
        },
        onDrag = { change, dragAmount ->
            change.consume()
            dragOffset += dragAmount
        },
        onDragEnd = {
            if (startedAtEdge) return@detectDragGestures
            val (dx, dy) = dragOffset
            if (abs(dx) > SWIPE_THRESHOLD_PX || abs(dy) > SWIPE_THRESHOLD_PX) {
                val direction = if (abs(dx) > abs(dy)) {
                    if (dx > 0) MoveDirection.RIGHT else MoveDirection.LEFT
                } else {
                    if (dy > 0) MoveDirection.DOWN else MoveDirection.UP
                }
                onSwipe(direction)
            }
        }
    )
}
