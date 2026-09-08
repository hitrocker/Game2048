package com.hitrocker.game2048.game

import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Result of a single [GameEngine.move] call.
 *
 * @param board The board *after* sliding/merging, but *before* a new random tile is spawned
 *              (spawning is a separate explicit step - see [GameEngine.spawnRandomTile] - so
 *              callers can tell the two visual events apart if they ever want to).
 * @param scoreGained Sum of the values of every tile created by a merge during this move. This
 *                     matches the original 2048 scoring rule: merging two 8s scores 16 points.
 * @param moved True if anything on the board actually changed position or value. A swipe into a
 *              wall where nothing can slide or merge reports `moved = false` so the caller knows
 *              not to spawn a new tile or count it as a turn.
 * @param mergedAway The tiles that were absorbed by a merge this move, each repositioned to the
 *              merge's destination cell. They are NOT part of [board] (the board only holds the
 *              surviving merged tile), but the UI renders them for one move so a merge visibly
 *              glides two tiles together instead of one blinking out. Empty when nothing merged.
 */
data class MoveResult(
    val board: Board,
    val scoreGained: Int,
    val moved: Boolean,
    val mergedAway: List<Tile> = emptyList()
)

/**
 * All the rules of 2048 live here, and nowhere else. GameEngine is a pure, stateless object:
 * every function takes a [Board] in and returns a new [Board] (or derived value) out, with no
 * mutable game state of its own. That makes it trivial to unit test and keeps the ViewModel
 * a thin coordinator instead of a place where game rules accidentally leak into UI code.
 */
object GameEngine {

    /** Probability that a newly spawned tile is a 2 (vs. a 4), matching the original game. */
    private const val SPAWN_TWO_PROBABILITY = 0.9

    // Monotonically increasing counter used to hand every new tile a unique id. This is the
    // only mutable state in the whole engine, and it exists purely so the UI layer has a stable
    // key to animate against - it has no effect on game rules or outcomes.
    private val idCounter = AtomicInteger(0)
    private fun nextId(): Int = idCounter.incrementAndGet()

    /**
     * Starts a brand new game on a [rows] x [cols] board: empty except for exactly two starter
     * tiles placed at random.
     */
    fun newGame(rows: Int, cols: Int, random: Random = Random.Default): Board {
        var board = Board.empty(rows, cols)
        board = spawnRandomTile(board, random)
        board = spawnRandomTile(board, random)
        return board
    }

    /**
     * Rebuilds a [Board] from a raw [values] matrix (`[row][col]`, 0 = empty cell), used to restore
     * a saved game. Each non-zero cell becomes a [Tile] with a fresh id from the shared counter (so
     * restored tiles never collide with tiles spawned later this session) and with the animation
     * flags cleared, so restoring does not replay any spawn/merge animation.
     */
    fun fromValues(values: List<List<Int>>): Board {
        val grid = values.mapIndexed { row, line ->
            line.mapIndexed { col, value ->
                if (value == 0) null else Tile(id = nextId(), value = value, row = row, col = col)
            }
        }
        return Board(grid)
    }

    /**
     * Returns a copy of [board] with one additional random tile placed in a random empty cell.
     * The new tile is a 2 with 90% probability or a 4 with 10% probability, matching the
     * original game. If the board is already full, [board] is returned unchanged.
     *
     * Every existing tile has its `isNew`/`isMerged` animation flags cleared, since those flags
     * are only meant to be true for a single move.
     */
    fun spawnRandomTile(board: Board, random: Random = Random.Default): Board {
        val emptyCells = board.emptyCells()
        if (emptyCells.isEmpty()) return board

        val (spawnRow, spawnCol) = emptyCells.random(random)
        val value = if (random.nextDouble() < SPAWN_TWO_PROBABILITY) 2 else 4
        val newTile = Tile(id = nextId(), value = value, row = spawnRow, col = spawnCol, isNew = true)

        val newGrid = board.grid.mapIndexed { row, line ->
            line.mapIndexed { col, tile ->
                if (row == spawnRow && col == spawnCol) {
                    newTile
                } else {
                    tile?.copy(isNew = false, isMerged = false)
                }
            }
        }
        return Board(newGrid)
    }

    /**
     * Slides every tile on [board] as far as possible in [direction], merging equal adjacent
     * tiles exactly once per move (a tile created by a merge cannot merge again in the same
     * move - e.g. a row of four 2s becomes two 4s, never one 8).
     */
    fun move(board: Board, direction: MoveDirection): MoveResult {
        // Every direction reduces to the same "compress + merge towards the start of the line"
        // operation; the only difference is which 4 cells make up a "line" and which end of it
        // is the destination. extractLines/buildGridFromLines handle that translation so the
        // merge logic itself only has to be written once, in mergeLine().
        val originalLines = extractLines(board, direction)
        val results = originalLines.map { line -> mergeLine(line) }
        val newLines = results.map { it.tiles }
        val absorbedLines = results.map { it.absorbed }

        val scoreGained = results.sumOf { it.score }
        val moved = originalLines.indices.any { i ->
            originalLines[i].map { it?.value } != newLines[i].map { it?.value }
        }

        val newGrid = buildGridFromLines(newLines, direction, board.rows, board.cols)
        // The absorbed tiles map through the exact same coordinate transform, so each lands on its
        // merge's destination cell; the UI animates them sliding there from their old position.
        val absorbedGrid = buildGridFromLines(absorbedLines, direction, board.rows, board.cols)
        val mergedAway = absorbedGrid.flatten().filterNotNull()
        return MoveResult(
            board = Board(newGrid),
            scoreGained = scoreGained,
            moved = moved,
            mergedAway = mergedAway
        )
    }

    /**
     * The board is unplayable once it is completely full AND no two horizontally or vertically
     * adjacent tiles share the same value (which would otherwise still allow a merge).
     */
    fun isGameOver(board: Board): Boolean {
        if (!board.isFull()) return false

        for (row in 0 until board.rows) {
            for (col in 0 until board.cols) {
                val value = board.tileAt(row, col)?.value ?: continue
                val rightValue = if (col + 1 < board.cols) board.tileAt(row, col + 1)?.value else null
                val downValue = if (row + 1 < board.rows) board.tileAt(row + 1, col)?.value else null
                if (value == rightValue || value == downValue) return false
            }
        }
        return true
    }

    /** True once any tile on the board has reached [winTarget]. */
    fun hasWon(board: Board, winTarget: Int): Boolean = board.tiles.any { it.value >= winTarget }

    // ---------------------------------------------------------------------------------------
    // Internal line-based move/merge implementation.
    //
    // A "line" is one row or one column, always expressed in "destination-first" order - i.e.
    // index 0 of the line is the cell tiles are sliding towards. This lets a single mergeLine()
    // function implement the move logic for all four directions.
    // ---------------------------------------------------------------------------------------

    /**
     * Result of compressing+merging a single line. [tiles] are the survivors (destination-first,
     * padded with nulls). [absorbed] is parallel to [tiles]: absorbed[i] is the tile that merged
     * into tiles[i] (or null if tiles[i] wasn't formed by a merge), kept so the UI can animate it.
     */
    private data class LineResult(
        val tiles: List<Tile?>,
        val absorbed: List<Tile?>,
        val score: Int
    )

    /**
     * Compresses [line] (removing gaps) and merges adjacent equal-value tiles once each,
     * scanning from the destination end (index 0) towards the far end. The result is padded
     * back out to the line's original length with nulls so it can be written straight back into
     * the grid (works for rows and columns of any length).
     *
     * Example (destination-first): [2, 2, 2, _] -> [4, 2, _, _], scoring 4 points.
     */
    private fun mergeLine(line: List<Tile?>): LineResult {
        val nonEmptyTiles = line.filterNotNull()
        val merged = mutableListOf<Tile>()
        val absorbed = mutableListOf<Tile?>()
        var score = 0

        var i = 0
        while (i < nonEmptyTiles.size) {
            val current = nonEmptyTiles[i]
            val next = nonEmptyTiles.getOrNull(i + 1)

            if (next != null && next.value == current.value) {
                // Merge: the pair collapses into a single tile worth double the value. We keep
                // the leading tile's id (so it visually "absorbs" the other and slides smoothly
                // into place) and mark it as merged so the UI can play the pulse animation.
                val mergedValue = current.value * 2
                merged += current.copy(value = mergedValue, isMerged = true, isNew = false)
                // The absorbed tile keeps its own id and original value; the UI slides it into the
                // merge cell (under the survivor) and drops it next move, so merges glide together
                // instead of one tile vanishing instantly.
                absorbed += next.copy(isNew = false, isMerged = false)
                score += mergedValue
                i += 2 // Both tiles are consumed; neither can merge again this move.
            } else {
                merged += current.copy(isMerged = false, isNew = false)
                absorbed += null
                i += 1
            }
        }

        val pad = line.size - merged.size
        return LineResult(
            tiles = merged + List(pad) { null as Tile? },
            absorbed = absorbed + List(pad) { null as Tile? },
            score = score
        )
    }

    /**
     * Extracts the lines (rows or columns) of [board] relevant to [direction], each reordered so
     * index 0 is the destination cell for that line. Horizontal moves produce one line per row
     * (each [Board.cols] long); vertical moves produce one line per column (each [Board.rows] long).
     */
    private fun extractLines(board: Board, direction: MoveDirection): List<List<Tile?>> {
        return when (direction) {
            MoveDirection.LEFT -> board.grid
            MoveDirection.RIGHT -> board.grid.map { row -> row.reversed() }
            MoveDirection.UP -> (0 until board.cols).map { col ->
                (0 until board.rows).map { row -> board.grid[row][col] }
            }
            MoveDirection.DOWN -> (0 until board.cols).map { col ->
                (0 until board.rows).map { row -> board.grid[row][col] }.reversed()
            }
        }
    }

    /**
     * Inverse of [extractLines]: takes the processed, destination-first [lines] and writes them
     * back into a real `[rows] x [cols]` grid, assigning each tile its final row/col.
     */
    private fun buildGridFromLines(
        lines: List<List<Tile?>>,
        direction: MoveDirection,
        rows: Int,
        cols: Int
    ): List<List<Tile?>> {
        val grid = MutableList(rows) { MutableList<Tile?>(cols) { null } }

        when (direction) {
            MoveDirection.LEFT -> {
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        grid[row][col] = lines[row][col]?.copy(row = row, col = col)
                    }
                }
            }
            MoveDirection.RIGHT -> {
                for (row in 0 until rows) {
                    for (pos in 0 until cols) {
                        val col = cols - 1 - pos
                        grid[row][col] = lines[row][pos]?.copy(row = row, col = col)
                    }
                }
            }
            MoveDirection.UP -> {
                for (col in 0 until cols) {
                    for (row in 0 until rows) {
                        grid[row][col] = lines[col][row]?.copy(row = row, col = col)
                    }
                }
            }
            MoveDirection.DOWN -> {
                for (col in 0 until cols) {
                    for (pos in 0 until rows) {
                        val row = rows - 1 - pos
                        grid[row][col] = lines[col][pos]?.copy(row = row, col = col)
                    }
                }
            }
        }

        return grid
    }
}
