package com.hitrocker.game2048.viewmodel

/**
 * One-shot feedback events emitted by [GameViewModel] for the UI layer to turn into haptics and
 * sound. These are deliberately kept separate from [GameUiState]: state describes *what the board
 * looks like now* (and survives recomposition/rotation), whereas an event is a *thing that just
 * happened* and should fire its feedback exactly once.
 */
enum class GameEvent {
    /** A swipe successfully moved at least one tile. */
    MOVE,

    /** At least one pair of tiles merged during the move. */
    MERGE,

    /** A 2048 tile was created for the first time this game. */
    WIN,

    /** The board filled up with no moves left. */
    GAME_OVER
}
