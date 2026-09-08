package com.hitrocker.game2048.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hitrocker.game2048.auth.AuthManager
import com.hitrocker.game2048.data.LeaderboardRepository
import com.hitrocker.game2048.data.PreferencesManager
import com.hitrocker.game2048.data.SavedGame
import com.hitrocker.game2048.game.Board
import com.hitrocker.game2048.game.GameEngine
import com.hitrocker.game2048.game.GridSize
import com.hitrocker.game2048.game.MoveDirection
import com.hitrocker.game2048.game.Tile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the UI needs to render a single frame of the game. Immutable by construction
 * (every property is a `val`, and [tiles] is a read-only List of immutable [Tile]s), so the
 * Compose compiler can treat it as stable and skip recomposition when nothing has actually
 * changed.
 */
@Immutable
data class GameUiState(
    val tiles: List<Tile> = emptyList(),
    // Tiles absorbed by a merge on the most recent move, kept for one move so the UI can animate
    // them sliding into the merge cell (then they're dropped). Not part of the logical board.
    val mergingTiles: List<Tile> = emptyList(),
    val score: Int = 0,
    val bestScore: Int = 0,
    val isGameOver: Boolean = false,
    val hasWon: Boolean = false,
    // True once the player has dismissed the "You Win" dialog and chosen to keep playing past
    // 2048. While false, hasWon = true is what triggers showing that dialog.
    val continuePlayingAfterWin: Boolean = false,
    // Number of successful moves made in the current game (no-op swipes don't count).
    val moveCount: Int = 0,
    // Dimensions of the current board, so the UI can render any shape.
    val rows: Int = GridSize.default.rows,
    val cols: Int = GridSize.default.cols,
    // Tile value that wins on the current board (varies by size).
    val winTarget: Int = GridSize.default.winTarget,
    // True when the last move can be undone (one level, cleared after undoing or a new game).
    val canUndo: Boolean = false
)

/**
 * Coordinates [GameEngine] (game rules) and [PreferencesManager] (persistence) and exposes a
 * single, immutable [uiState] StateFlow for the UI to collect. The ViewModel intentionally
 * contains no game *rules* of its own - it only decides *when* to call the engine and how to
 * fold the result into UI state.
 */
class GameViewModel(
    private val preferencesManager: PreferencesManager,
    private val leaderboardRepository: LeaderboardRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // One-shot feedback events (haptics/sound). replay = 0 so a freshly-subscribed collector never
    // re-fires a stale event; the buffer lets tryEmit succeed even if collection briefly lags.
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    // Sound/haptics preferences, mirrored from the shared DataStore so the UI can gate feedback.
    // Toggling them in Settings updates these without any extra wiring (same DataStore instance).
    val soundEnabled: StateFlow<Boolean> = preferencesManager.soundEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val hapticsEnabled: StateFlow<Boolean> = preferencesManager.hapticsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val showMoveCounter: StateFlow<Boolean> = preferencesManager.showMoveCounterFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val animationsEnabled: StateFlow<Boolean> = preferencesManager.animationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // The "real" board lives here, not in GameUiState. We only ever publish board.tiles to the
    // UI; keeping the full Board internal avoids the UI layer reaching into engine internals.
    private var board: Board = Board.empty(GridSize.default.rows, GridSize.default.cols)

    // The size currently being played. Drives the win target and the dimensions published to the UI.
    private var gridSize: GridSize = GridSize.default

    // Single-level undo snapshot of the state just before the most recent move. Session-only (not
    // persisted); null when there's nothing to undo.
    private var undoSnapshot: UndoSnapshot? = null

    private data class UndoSnapshot(
        val board: Board,
        val score: Int,
        val moveCount: Int,
        val hasWon: Boolean,
        val continuePlayingAfterWin: Boolean
    )

    init {
        // Restore any in-progress game before deciding what to show. Each size has its own saved
        // slot, so we resume the last selected size's game if one exists; otherwise we start fresh.
        // Done in one coroutine so we never briefly overwrite a restored board.
        viewModelScope.launch {
            preferencesManager.migrateLegacySaveIfNeeded()
            val selectedSize = preferencesManager.selectedSizeFlow.first()
            val savedGame = preferencesManager.readSavedGame(selectedSize)
            if (savedGame != null) {
                resumeSavedGame(selectedSize, savedGame)
            } else {
                startNewGame(selectedSize)
            }
        }
    }

    /**
     * Entry point used by the Home screen's Play button. Keeps the current game running if it's
     * already the requested [size] and still in progress; otherwise resumes that size's own saved
     * game (so switching sizes never loses progress), or starts fresh if there's nothing to resume.
     */
    fun playSize(size: GridSize) {
        val state = _uiState.value
        if (size == gridSize && state.tiles.isNotEmpty() && !state.isGameOver) return
        viewModelScope.launch {
            val savedGame = preferencesManager.readSavedGame(size)
            if (savedGame != null) {
                resumeSavedGame(size, savedGame)
            } else {
                startNewGame(size)
            }
        }
    }

    /** Rebuilds [size]'s board from a [SavedGame] and publishes it as the active game. */
    private suspend fun resumeSavedGame(size: GridSize, savedGame: SavedGame) {
        gridSize = size
        board = GameEngine.fromValues(savedGame.grid)
        undoSnapshot = null
        val best = preferencesManager.bestScoreFlow(size).first()
        preferencesManager.setSelectedSize(size)
        _uiState.update {
            it.copy(
                tiles = board.tiles,
                mergingTiles = emptyList(),
                rows = size.rows,
                cols = size.cols,
                winTarget = size.winTarget,
                score = savedGame.score,
                bestScore = best,
                hasWon = savedGame.hasWon,
                continuePlayingAfterWin = savedGame.continueAfterWin,
                isGameOver = GameEngine.isGameOver(board),
                moveCount = savedGame.moveCount,
                canUndo = false
            )
        }
    }

    /** Resets to a fresh game of [size], persisting it as the selected size. Best score is kept. */
    fun startNewGame(size: GridSize = gridSize) {
        gridSize = size
        board = GameEngine.newGame(size.rows, size.cols)
        undoSnapshot = null
        _uiState.update { current ->
            current.copy(
                tiles = board.tiles,
                mergingTiles = emptyList(),
                rows = size.rows,
                cols = size.cols,
                winTarget = size.winTarget,
                score = 0,
                isGameOver = false,
                hasWon = false,
                continuePlayingAfterWin = false,
                moveCount = 0,
                canUndo = false
            )
        }
        viewModelScope.launch { preferencesManager.setSelectedSize(size) }
        loadBestScoreFor(size)
        persistGameState()
    }

    private fun loadBestScoreFor(size: GridSize) {
        viewModelScope.launch {
            val best = preferencesManager.bestScoreFlow(size).first()
            _uiState.update { it.copy(bestScore = best) }
        }
    }

    /**
     * Applies a swipe in [direction]: slides/merges tiles, spawns a new tile if anything moved,
     * updates score/best score, and re-evaluates win/game-over conditions.
     */
    fun onSwipe(direction: MoveDirection) {
        val state = _uiState.value
        if (state.isGameOver) return // No more moves accepted once the game has ended.

        val moveResult = GameEngine.move(board, direction)
        if (!moveResult.moved) return // Swipe into a wall: nothing changed, ignore it entirely.

        // Snapshot the pre-move state so this move (and its random spawn) can be undone once.
        undoSnapshot = UndoSnapshot(
            board = board,
            score = state.score,
            moveCount = state.moveCount,
            hasWon = state.hasWon,
            continuePlayingAfterWin = state.continuePlayingAfterWin
        )

        val boardWithNewTile = GameEngine.spawnRandomTile(moveResult.board)
        board = boardWithNewTile

        val newScore = state.score + moveResult.scoreGained
        val newBestScore = maxOf(newScore, state.bestScore)
        if (newBestScore > state.bestScore) {
            persistBestScore(newBestScore)
            submitToLeaderboard(newBestScore)
        }

        val alreadyWon = state.hasWon
        val hasWon = alreadyWon || GameEngine.hasWon(boardWithNewTile, gridSize.winTarget)
        val isGameOver = GameEngine.isGameOver(boardWithNewTile)

        _uiState.update { current ->
            current.copy(
                tiles = boardWithNewTile.tiles,
                mergingTiles = moveResult.mergedAway,
                score = newScore,
                bestScore = newBestScore,
                hasWon = hasWon,
                isGameOver = isGameOver,
                moveCount = current.moveCount + 1,
                canUndo = true
            )
        }

        // Emit feedback events after state is updated. MOVE always fires (we already returned early
        // on a no-op swipe); MERGE only when something actually merged; WIN only on the first 2048.
        _events.tryEmit(GameEvent.MOVE)
        if (moveResult.scoreGained > 0) _events.tryEmit(GameEvent.MERGE)
        if (!alreadyWon && hasWon) _events.tryEmit(GameEvent.WIN)
        if (isGameOver) _events.tryEmit(GameEvent.GAME_OVER)

        persistGameState()
    }

    /** Called when the player dismisses the "You Win" dialog to keep playing past 2048. */
    fun dismissWinDialogAndContinue() {
        _uiState.update { it.copy(continuePlayingAfterWin = true) }
        persistGameState()
    }

    /**
     * Reverts the most recent move (single level). Restores the exact pre-move board - including
     * discarding that move's random spawn - and clears the game-over state if the undone move was
     * the fatal one. The best score is intentionally not rolled back.
     */
    fun undo() {
        val snapshot = undoSnapshot ?: return
        board = snapshot.board
        undoSnapshot = null
        _uiState.update { current ->
            current.copy(
                tiles = board.tiles,
                mergingTiles = emptyList(),
                score = snapshot.score,
                moveCount = snapshot.moveCount,
                hasWon = snapshot.hasWon,
                continuePlayingAfterWin = snapshot.continuePlayingAfterWin,
                isGameOver = false,
                canUndo = false
            )
        }
        persistGameState()
    }

    /** Persists the sound preference (toggled from the in-game settings sheet). */
    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSoundEnabled(enabled) }
    }

    /** Persists the haptics preference (toggled from the in-game settings sheet). */
    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setHapticsEnabled(enabled) }
    }

    /** Persists the show-move-counter preference (toggled from the settings sheet). */
    fun setShowMoveCounter(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setShowMoveCounter(enabled) }
    }

    /** Persists the animations preference (toggled from the settings sheet). */
    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAnimationsEnabled(enabled) }
    }

    private fun persistBestScore(score: Int) {
        viewModelScope.launch { preferencesManager.saveBestScore(gridSize, score) }
    }

    /**
     * Publishes a new per-size best to the global leaderboard. Fire-and-forget: failures (offline,
     * not yet signed in) are swallowed so gameplay is never affected; the score still lives locally
     * and will be re-published on the next best or after signing in.
     */
    private fun submitToLeaderboard(score: Int) {
        val uid = authManager.currentUid() ?: return
        val size = gridSize
        viewModelScope.launch {
            val name = authManager.resolveName(preferencesManager.playerNameFlow.first())
            runCatching { leaderboardRepository.submitScore(size, uid, name, score) }
        }
    }

    /**
     * Saves the current board, score, and win/continue flags so the game can be resumed after the
     * app is closed or the process is killed. Fire-and-forget on [viewModelScope]; DataStore writes
     * happen off the UI thread.
     */
    private fun persistGameState() {
        val state = _uiState.value
        val size = gridSize
        val grid = board.grid.map { line -> line.map { it?.value ?: 0 } }
        viewModelScope.launch {
            preferencesManager.saveGameState(
                size = size,
                grid = grid,
                score = state.score,
                hasWon = state.hasWon,
                continueAfterWin = state.continuePlayingAfterWin,
                moveCount = state.moveCount
            )
        }
    }
}

/**
 * Manual ViewModelProvider.Factory since this project intentionally has no DI framework.
 * Constructs [GameViewModel] with the [PreferencesManager] it needs.
 */
class GameViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val leaderboardRepository: LeaderboardRepository,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(preferencesManager, leaderboardRepository, authManager) as T
    }
}
