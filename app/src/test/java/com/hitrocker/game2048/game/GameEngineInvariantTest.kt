package com.hitrocker.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Invariant / fuzz tests aimed at the "blocks disappear after 2048" report.
 *
 * The key idea: a move must *conserve the total value* on the board, because merging two equal
 * tiles (e.g. 2 + 2 -> 4) keeps the sum the same. If tiles were silently vanishing from game
 * state, this sum would drop. These tests therefore drive long, randomized games and assert the
 * invariants after every move. They run as plain JVM tests (GameEngine is pure - no device needed).
 */
class GameEngineInvariantTest {

    private val directions = listOf(
        MoveDirection.LEFT, MoveDirection.RIGHT, MoveDirection.UP, MoveDirection.DOWN
    )

    @Test
    fun `randomized games never lose tiles or value`() {
        repeat(50) { seed ->
            val random = Random(seed)
            var board = GameEngine.newGame(4, 4, random)
            assertInvariants(board, "seed $seed start")

            var guard = 0
            while (!GameEngine.isGameOver(board) && guard++ < 5_000) {
                val before = board
                val dir = directions.random(random)
                val result = GameEngine.move(before, dir)

                // Core check: a move never changes the total value on the board.
                assertEquals(
                    "move $dir (seed $seed) lost/gained value",
                    valueSum(before), valueSum(result.board)
                )
                // A move can only merge tiles, never create them.
                assertTrue(
                    "move $dir (seed $seed) increased tile count",
                    result.board.tiles.size <= before.tiles.size
                )

                board = if (result.moved) {
                    val sumBeforeSpawn = valueSum(result.board)
                    val countBeforeSpawn = result.board.tiles.size
                    val spawned = GameEngine.spawnRandomTile(result.board, random)
                    assertEquals(
                        "spawn did not add exactly one tile (seed $seed)",
                        countBeforeSpawn + 1, spawned.tiles.size
                    )
                    val delta = valueSum(spawned) - sumBeforeSpawn
                    assertTrue("spawned $delta, expected 2 or 4 (seed $seed)", delta == 2 || delta == 4)
                    spawned
                } else {
                    result.board
                }
                assertInvariants(board, "seed $seed move $guard")
            }
        }
    }

    @Test
    fun `continuing past 2048 conserves every tile`() {
        // Two 1024s ready to merge into 2048; the rest of the board is full and varied so subsequent
        // play keeps the board busy (the exact scenario the tester described).
        var board = boardOf(
            listOf(1024, 1024, 2, 4),
            listOf(2, 4, 8, 16),
            listOf(4, 8, 16, 32),
            listOf(8, 16, 32, 64)
        )

        val won = GameEngine.move(board, MoveDirection.LEFT)
        assertTrue("expected a 2048 after merging the two 1024s", GameEngine.hasWon(won.board, winTarget = 2048))
        board = GameEngine.spawnRandomTile(won.board, Random(0))

        val random = Random(7)
        var guard = 0
        while (!GameEngine.isGameOver(board) && guard++ < 3_000) {
            val before = board
            val result = GameEngine.move(before, directions.random(random))
            assertEquals("a post-2048 move dropped value", valueSum(before), valueSum(result.board))
            board = if (result.moved) GameEngine.spawnRandomTile(result.board, random) else result.board
            assertInvariants(board, "post-2048 move $guard")
        }
    }

    private fun assertInvariants(board: Board, where: String) {
        // Unique ids matter because the UI renders tiles by key(tile.id); a collision would make a
        // tile silently disappear on screen.
        val ids = board.tiles.map { it.id }
        assertEquals("$where: duplicate tile ids $ids", ids.size, ids.toSet().size)
        board.tiles.forEach { tile ->
            assertTrue(
                "$where: non power-of-two value ${tile.value}",
                tile.value >= 2 && (tile.value and (tile.value - 1)) == 0
            )
        }
    }

    private fun valueSum(board: Board): Int = board.tiles.sumOf { it.value }

    /** Builds a [Board] from plain Int rows (0 = empty). Ids are offset to stay distinct. */
    private fun boardOf(vararg rows: List<Int>): Board = Board(
        rows.mapIndexed { row, line ->
            line.mapIndexed { col, value ->
                if (value == 0) null else Tile(id = 1_000 + row * 10 + col, value = value, row = row, col = col)
            }
        }
    )
}
