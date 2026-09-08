package com.hitrocker.game2048.game

import androidx.compose.runtime.Immutable

/**
 * Immutable representation of a single tile on the 2048 board.
 *
 * @param id Stable identity used by Compose to track a tile across recompositions/moves. This
 *           is what lets the UI animate a tile sliding from its old cell to its new one instead
 *           of treating every move as "delete every tile, draw new ones".
 * @param value The numeric value displayed on the tile (2, 4, 8, ... 2048, ...).
 * @param row Zero-based row index (0..3) on the board.
 * @param col Zero-based column index (0..3) on the board.
 * @param isNew True only for the move during which this tile was spawned. The UI uses this to
 *              play a "pop in" appearance animation; the flag is cleared on the following move.
 * @param isMerged True only for the move during which this tile was created by merging two
 *                  equal tiles. The UI uses this to play a brief "pulse" scale animation.
 */
@Immutable
data class Tile(
    val id: Int,
    val value: Int,
    val row: Int,
    val col: Int,
    val isNew: Boolean = false,
    val isMerged: Boolean = false
)
