package com.hitrocker.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GameEngine]. Since GameEngine is pure (no Android dependencies), these run as
 * plain JVM tests with no emulator/device required.
 *
 * Boards are built and read back as plain Int grids (0 = empty) via the [boardOf]/[values]
 * helpers below, which keeps each test's intent readable at a glance.
 */
class GameEngineTest {

    // ---------------------------------------------------------------------------------------
    // Movement (compression with no merging involved)
    // ---------------------------------------------------------------------------------------

    @Test
    fun `move left compacts tiles toward the left edge without merging`() {
        val board = boardOf(
            listOf(0, 2, 0, 4),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertEquals(listOf(2, 4, 0, 0), result.board.values()[0])
        assertTrue(result.moved)
        assertEquals(0, result.scoreGained)
    }

    @Test
    fun `move right compacts tiles toward the right edge without merging`() {
        val board = boardOf(
            listOf(2, 0, 4, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.RIGHT)

        assertEquals(listOf(0, 0, 2, 4), result.board.values()[0])
        assertTrue(result.moved)
    }

    @Test
    fun `move up compacts tiles toward the top edge without merging`() {
        val board = boardOf(
            listOf(0, 0, 0, 0),
            listOf(2, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(4, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.UP)
        val firstColumn = result.board.values().map { it[0] }

        assertEquals(listOf(2, 4, 0, 0), firstColumn)
        assertTrue(result.moved)
    }

    @Test
    fun `move down compacts tiles toward the bottom edge without merging`() {
        val board = boardOf(
            listOf(2, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(4, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.DOWN)
        val firstColumn = result.board.values().map { it[0] }

        assertEquals(listOf(0, 0, 2, 4), firstColumn)
        assertTrue(result.moved)
    }

    @Test
    fun `a move that changes nothing is reported as not moved`() {
        // Already fully packed against the left edge with no equal neighbors - LEFT is a no-op.
        val board = boardOf(
            listOf(2, 4, 8, 16),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertFalse(result.moved)
        assertEquals(0, result.scoreGained)
    }

    // ---------------------------------------------------------------------------------------
    // Merge rules
    // ---------------------------------------------------------------------------------------

    @Test
    fun `two equal tiles merge into one with double the value`() {
        val board = boardOf(
            listOf(2, 2, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertEquals(listOf(4, 0, 0, 0), result.board.values()[0])
        assertEquals(4, result.scoreGained)
    }

    @Test
    fun `a full row of four equal tiles merges only into two pairs, never one tile`() {
        // [2,2,2,2] -> [4,4,0,0]. It must NOT become [8,4,0,0] - a tile created by a merge
        // cannot merge again within the same move.
        val board = boardOf(
            listOf(2, 2, 2, 2),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertEquals(listOf(4, 4, 0, 0), result.board.values()[0])
        assertEquals(8, result.scoreGained)
    }

    @Test
    fun `three equal tiles in a row merge only the leading pair`() {
        // [2,2,2,0] -> [4,2,0,0], scanning from the destination edge inward.
        val board = boardOf(
            listOf(2, 2, 2, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertEquals(listOf(4, 2, 0, 0), result.board.values()[0])
        assertEquals(4, result.scoreGained)
    }

    @Test
    fun `tiles with different values never merge`() {
        val board = boardOf(
            listOf(2, 4, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertEquals(listOf(2, 4, 0, 0), result.board.values()[0])
        assertFalse(result.moved)
        assertEquals(0, result.scoreGained)
    }

    @Test
    fun `score accumulates across multiple independent merges in a single move`() {
        // [2,2,4,4] -> [4,8,0,0]: two separate merges happen in the same move.
        val board = boardOf(
            listOf(2, 2, 4, 4),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertEquals(listOf(4, 8, 0, 0), result.board.values()[0])
        assertEquals(12, result.scoreGained) // 4 (from 2+2) + 8 (from 4+4)
    }

    // ---------------------------------------------------------------------------------------
    // Game-over detection
    // ---------------------------------------------------------------------------------------

    @Test
    fun `game over is detected on a full board with no adjacent equal values`() {
        val board = boardOf(
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2),
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2)
        )

        assertTrue(GameEngine.isGameOver(board))
    }

    @Test
    fun `game is not over on a full board that still has one adjacent merge available`() {
        val board = boardOf(
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2),
            listOf(2, 4, 2, 2), // last two cells in this row are equal - a merge is still possible
            listOf(4, 2, 4, 2)
        )

        assertFalse(GameEngine.isGameOver(board))
    }

    @Test
    fun `game is not over while there are still empty cells, regardless of values`() {
        val board = boardOf(
            listOf(2, 4, 8, 16),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        assertFalse(GameEngine.isGameOver(board))
    }

    // ---------------------------------------------------------------------------------------
    // Win detection
    // ---------------------------------------------------------------------------------------

    @Test
    fun `hasWon is true once a tile reaches the win target`() {
        val board = boardOf(
            listOf(2048, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        assertTrue(GameEngine.hasWon(board, winTarget = 2048))
    }

    @Test
    fun `hasWon is false when the highest tile is below the win target`() {
        val board = boardOf(
            listOf(1024, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0)
        )

        assertFalse(GameEngine.hasWon(board, winTarget = 2048))
    }

    @Test
    fun `hasWon respects a lower win target for small boards`() {
        val board = boardOf(
            listOf(256, 0, 0),
            listOf(0, 0, 0),
            listOf(0, 0, 0)
        )

        assertTrue(GameEngine.hasWon(board, winTarget = 256))
        assertFalse(GameEngine.hasWon(board, winTarget = 2048))
    }

    // ---------------------------------------------------------------------------------------
    // Non-square boards
    // ---------------------------------------------------------------------------------------

    @Test
    fun `move works on a portrait rectangle where rows and cols differ`() {
        // 2 columns x 4 rows. Sliding up should compact the left column and merge the two 2s.
        val board = boardOf(
            listOf(2, 0),
            listOf(2, 0),
            listOf(0, 0),
            listOf(4, 0)
        )

        val result = GameEngine.move(board, MoveDirection.UP)
        val leftColumn = result.board.values().map { it[0] }

        assertEquals(listOf(4, 4, 0, 0), leftColumn)
        assertTrue(result.moved)
        assertEquals(4, result.scoreGained)
    }

    @Test
    fun `move left on a wide row merges across the full width`() {
        // 1 row x 4 cols feeding into a taller board; verify horizontal merge over differing dims.
        val board = boardOf(
            listOf(2, 2, 4, 4),
            listOf(0, 0, 0, 0)
        )

        val result = GameEngine.move(board, MoveDirection.LEFT)

        assertEquals(listOf(4, 8, 0, 0), result.board.values()[0])
        assertEquals(12, result.scoreGained)
    }

    // ---------------------------------------------------------------------------------------
    // Spawning / new game setup
    // ---------------------------------------------------------------------------------------

    @Test
    fun `spawnRandomTile adds exactly one tile with value 2 or 4 to an empty board`() {
        val board = GameEngine.spawnRandomTile(Board.empty(4, 4))

        assertEquals(1, board.tiles.size)
        val spawnedValue = board.tiles.first().value
        assertTrue(spawnedValue == 2 || spawnedValue == 4)
    }

    @Test
    fun `spawnRandomTile does nothing when the board is already full`() {
        val fullBoard = boardOf(
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2),
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2)
        )

        val result = GameEngine.spawnRandomTile(fullBoard)

        assertEquals(fullBoard.values(), result.values())
    }

    @Test
    fun `newGame starts with exactly two tiles on an otherwise empty board`() {
        val board = GameEngine.newGame(4, 4)

        assertEquals(2, board.tiles.size)
        assertTrue(board.tiles.all { it.value == 2 || it.value == 4 })
    }

    @Test
    fun `newGame honors requested dimensions`() {
        val board = GameEngine.newGame(rows = 7, cols = 5)

        assertEquals(7, board.rows)
        assertEquals(5, board.cols)
        assertEquals(2, board.tiles.size)
    }

    // ---------------------------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------------------------

    /** Builds a [Board] from plain Int rows, where 0 means an empty cell. */
    private fun boardOf(vararg rows: List<Int>): Board {
        val grid = rows.mapIndexed { row, line ->
            line.mapIndexed { col, value ->
                if (value == 0) null else Tile(id = row * 10 + col, value = value, row = row, col = col)
            }
        }
        return Board(grid)
    }

    /** Reads a [Board] back out as plain Int rows (0 = empty), mirroring [boardOf]. */
    private fun Board.values(): List<List<Int>> = grid.map { row -> row.map { it?.value ?: 0 } }
}
