package com.hitrocker.game2048.game

import androidx.compose.runtime.Immutable

/**
 * Immutable game board of arbitrary dimensions.
 *
 * The board is stored as a [grid] of nullable [Tile]s indexed `[row][col]`, which makes
 * neighbor lookups and game-over detection straightforward. [tiles] exposes the same data as a
 * flat list, which is what the UI layer actually renders. Dimensions are derived from the grid
 * itself ([rows] / [cols]) so a single Board type supports every shape in [GridSize].
 */
@Immutable
data class Board(val grid: List<List<Tile?>>) {

    /** Number of rows (height) of this board. */
    val rows: Int get() = grid.size

    /** Number of columns (width) of this board. */
    val cols: Int get() = if (grid.isEmpty()) 0 else grid[0].size

    /** Flat list of every non-empty tile currently on the board. */
    val tiles: List<Tile> by lazy { grid.flatten().filterNotNull() }

    /** Returns the tile occupying [row]/[col], or null if that cell is empty. */
    fun tileAt(row: Int, col: Int): Tile? = grid[row][col]

    /** Coordinates of every empty cell on the board. */
    fun emptyCells(): List<Pair<Int, Int>> = buildList {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (grid[row][col] == null) add(row to col)
            }
        }
    }

    /** True once every cell is occupied by a tile. */
    fun isFull(): Boolean = emptyCells().isEmpty()

    companion object {
        /** A [rows] x [cols] board with no tiles on it at all. */
        fun empty(rows: Int, cols: Int): Board = Board(List(rows) { List(cols) { null } })
    }
}
