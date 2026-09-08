package com.hitrocker.game2048.game

/**
 * The set of board shapes the player can choose from. Each value is the single source of truth for
 * its dimensions ([cols] x [rows]), the tile value that counts as a win ([winTarget]), and the
 * human-readable [label] shown in the picker.
 *
 * Labels follow the classic "<width>x<height>" convention, so [R3X4] is 3 columns wide and 4 rows
 * tall (a portrait rectangle). The smallest square uses a lower [winTarget] because reaching 2048 on
 * a 3x3 grid is effectively impossible.
 */
enum class GridSize(
    val cols: Int,
    val rows: Int,
    val winTarget: Int
) {
    // Squares (page 1 of the picker).
    S3X3(3, 3, 256),
    S4X4(4, 4, 2048),
    S5X5(5, 5, 2048),
    S6X6(6, 6, 2048),

    // Portrait rectangles (page 2 of the picker).
    R3X4(3, 4, 2048),
    R3X5(3, 5, 2048),
    R4X6(4, 6, 2048),
    R5X7(5, 7, 2048);

    /** Display label, e.g. "4×4" or "3×5" (columns × rows). */
    val label: String get() = "$cols×$rows"

    /** Aspect ratio (width / height) used to lay out the board and its previews. */
    val aspectRatio: Float get() = cols.toFloat() / rows.toFloat()

    companion object {
        /** Square boards, in picker order. */
        val squares: List<GridSize> = listOf(S3X3, S4X4, S5X5, S6X6)

        /** Rectangular boards, in picker order. */
        val rectangles: List<GridSize> = listOf(R3X4, R3X5, R4X6, R5X7)

        /** The size selected by default before the player has chosen anything. */
        val default: GridSize = S4X4

        /** Finds a size by its [label], or null if none matches (e.g. a corrupt/old preference). */
        fun fromLabel(label: String?): GridSize? = entries.firstOrNull { it.label == label }

        /** Finds a size by raw dimensions, used when restoring a saved game. */
        fun fromDims(cols: Int, rows: Int): GridSize? =
            entries.firstOrNull { it.cols == cols && it.rows == rows }
    }
}
