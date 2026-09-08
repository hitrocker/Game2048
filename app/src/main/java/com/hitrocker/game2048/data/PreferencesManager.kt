package com.hitrocker.game2048.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hitrocker.game2048.game.GridSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property creating a single, app-wide DataStore instance backed by this name.
private val Context.dataStore by preferencesDataStore(name = "game_2048_prefs")

/**
 * Snapshot of an in-progress game, as read back from disk. [grid] is a [rows] x [cols] matrix of
 * tile values (0 = empty cell), so a saved game restores into the exact board shape it was played
 * on.
 */
data class SavedGame(
    val grid: List<List<Int>>,
    val score: Int,
    val hasWon: Boolean,
    val continueAfterWin: Boolean,
    val moveCount: Int,
    val rows: Int,
    val cols: Int
) {
    /** True if at least one cell is occupied (otherwise there is nothing worth restoring). */
    val hasTiles: Boolean = grid.any { row -> row.any { it != 0 } }
}

/**
 * Thin wrapper around Jetpack DataStore Preferences responsible for persisting:
 * - the player's best score (tracked per board size), and
 * - the full in-progress game (board shape, tiles, score, win/continue flags, moves) so closing and
 *   reopening the app resumes exactly where the player left off, and
 * - which board size the player last selected, plus the sound/haptics/move-counter settings.
 *
 * Kept deliberately small: this is the only piece of the app that knows persistence exists at all.
 */
class PreferencesManager(private val context: Context) {

    // Legacy single best-score key, kept only so existing 4x4 records migrate forward.
    private val legacyBestScoreKey = intPreferencesKey("best_score")
    // Legacy single saved-game keys (pre per-size saves). Migrated into the per-size slots on first
    // launch after the update, then removed.
    private val legacyBoardKey = stringPreferencesKey("board")
    private val legacyBoardRowsKey = intPreferencesKey("board_rows")
    private val legacyBoardColsKey = intPreferencesKey("board_cols")
    private val legacyCurrentScoreKey = intPreferencesKey("current_score")
    private val legacyHasWonKey = booleanPreferencesKey("has_won")
    private val legacyContinueKey = booleanPreferencesKey("continue_after_win")
    private val legacyMoveCountKey = intPreferencesKey("move_count")
    private val selectedSizeKey = stringPreferencesKey("selected_size")
    private val playerNameKey = stringPreferencesKey("player_name")
    private val soundEnabledKey = booleanPreferencesKey("sound_enabled")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val showMoveCounterKey = booleanPreferencesKey("show_move_counter")
    private val animationsEnabledKey = booleanPreferencesKey("animations_enabled")
    private val hasSeenHowToPlayKey = booleanPreferencesKey("has_seen_how_to_play")

    // Best score is stored separately for each board size so a high 6x6 score never shadows a 4x4.
    private fun bestScoreKey(size: GridSize) = intPreferencesKey("best_score_${size.label}")

    // Each board size has its own saved-game slot, so progress on one shape is never clobbered by
    // playing another. Board dimensions are implied by the size, so they aren't stored separately.
    private fun boardKey(size: GridSize) = stringPreferencesKey("board_${size.label}")
    private fun savedScoreKey(size: GridSize) = intPreferencesKey("current_score_${size.label}")
    private fun savedHasWonKey(size: GridSize) = booleanPreferencesKey("has_won_${size.label}")
    private fun savedContinueKey(size: GridSize) = booleanPreferencesKey("continue_after_win_${size.label}")
    private fun savedMoveCountKey(size: GridSize) = intPreferencesKey("move_count_${size.label}")

    /**
     * Emits the persisted best score for [size], defaulting to 0. The default size additionally
     * falls back to the pre-multi-size global record so old saves carry over once.
     */
    fun bestScoreFlow(size: GridSize): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[bestScoreKey(size)]
            ?: (if (size == GridSize.default) preferences[legacyBestScoreKey] else null)
            ?: 0
    }

    /** Persists [score] as the new best score for [size]. */
    suspend fun saveBestScore(size: GridSize, score: Int) {
        context.dataStore.edit { preferences ->
            preferences[bestScoreKey(size)] = score
        }
    }

    /**
     * Reads every stored per-size best in one shot. Used when (re)publishing the player's bests to
     * the leaderboard after signing in, so their device records appear under their account.
     */
    suspend fun allBestScores(): Map<GridSize, Int> {
        val preferences = context.dataStore.data.first()
        return GridSize.entries.associateWith { size ->
            preferences[bestScoreKey(size)]
                ?: (if (size == GridSize.default) preferences[legacyBestScoreKey] else null)
                ?: 0
        }
    }

    /** The display name the player wants on the leaderboard (empty until they set one). */
    val playerNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[playerNameKey] ?: ""
    }

    /** Persists the player's chosen leaderboard display name. */
    suspend fun setPlayerName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[playerNameKey] = name.trim().take(20)
        }
    }

    /** The board size the player last selected (defaults to [GridSize.default]). */
    val selectedSizeFlow: Flow<GridSize> = context.dataStore.data.map { preferences ->
        GridSize.fromLabel(preferences[selectedSizeKey]) ?: GridSize.default
    }

    /** Persists the player's board-size choice. */
    suspend fun setSelectedSize(size: GridSize) {
        context.dataStore.edit { preferences ->
            preferences[selectedSizeKey] = size.label
        }
    }

    /** Whether sound effects are enabled (defaults to true until the user changes it). */
    val soundEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[soundEnabledKey] ?: true
    }

    /** Whether haptic feedback is enabled (defaults to true until the user changes it). */
    val hapticsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[hapticsEnabledKey] ?: true
    }

    /** Persists the sound-effects on/off preference. */
    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[soundEnabledKey] = enabled
        }
    }

    /** Persists the haptic-feedback on/off preference. */
    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[hapticsEnabledKey] = enabled
        }
    }

    /** Whether the move counter under the board is shown (defaults to true). */
    val showMoveCounterFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[showMoveCounterKey] ?: true
    }

    /** Persists the show-move-counter preference. */
    suspend fun setShowMoveCounter(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[showMoveCounterKey] = enabled
        }
    }

    /**
     * Whether tile slide/pop/merge animations play (defaults to true). Turning this off makes tiles
     * snap instantly, which some players prefer on slower devices.
     */
    val animationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[animationsEnabledKey] ?: true
    }

    /** Persists the animations on/off preference. */
    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[animationsEnabledKey] = enabled
        }
    }

    /**
     * Whether the player has seen the "How to play" guide. Defaults to false so the dialog auto-shows
     * once for brand-new players, then is flipped to true so it never interrupts them again.
     */
    val hasSeenHowToPlayFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[hasSeenHowToPlayKey] ?: false
    }

    /** Records that the player has seen the how-to-play guide. */
    suspend fun setHasSeenHowToPlay() {
        context.dataStore.edit { preferences ->
            preferences[hasSeenHowToPlayKey] = true
        }
    }

    /**
     * Reads the saved in-progress game for [size], or null if nothing meaningful is stored for that
     * size yet (no board key, or a board with no tiles on it). The board shape is taken from [size].
     */
    suspend fun readSavedGame(size: GridSize): SavedGame? {
        val preferences = context.dataStore.data.first()
        val encoded = preferences[boardKey(size)] ?: return null
        val grid = decode(encoded, size.rows, size.cols) ?: return null
        val saved = SavedGame(
            grid = grid,
            score = preferences[savedScoreKey(size)] ?: 0,
            hasWon = preferences[savedHasWonKey(size)] ?: false,
            continueAfterWin = preferences[savedContinueKey(size)] ?: false,
            moveCount = preferences[savedMoveCountKey(size)] ?: 0,
            rows = size.rows,
            cols = size.cols
        )
        return if (saved.hasTiles) saved else null
    }

    /** Persists the current game in [size]'s slot so it can be restored after the app is closed. */
    suspend fun saveGameState(
        size: GridSize,
        grid: List<List<Int>>,
        score: Int,
        hasWon: Boolean,
        continueAfterWin: Boolean,
        moveCount: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[boardKey(size)] = encode(grid)
            preferences[savedScoreKey(size)] = score
            preferences[savedHasWonKey(size)] = hasWon
            preferences[savedContinueKey(size)] = continueAfterWin
            preferences[savedMoveCountKey(size)] = moveCount
        }
    }

    /**
     * One-time migration of the old single-slot saved game (written before per-size saves existed)
     * into the matching size's slot. Safe to call on every launch: it does nothing once the legacy
     * keys are gone, and never overwrites a per-size save that already exists.
     */
    suspend fun migrateLegacySaveIfNeeded() {
        context.dataStore.edit { preferences ->
            val encoded = preferences[legacyBoardKey] ?: return@edit
            val rows = preferences[legacyBoardRowsKey] ?: 4
            val cols = preferences[legacyBoardColsKey] ?: 4
            val size = GridSize.fromDims(cols, rows)
            if (size != null && preferences[boardKey(size)] == null) {
                preferences[boardKey(size)] = encoded
                preferences[savedScoreKey(size)] = preferences[legacyCurrentScoreKey] ?: 0
                preferences[savedHasWonKey(size)] = preferences[legacyHasWonKey] ?: false
                preferences[savedContinueKey(size)] = preferences[legacyContinueKey] ?: false
                preferences[savedMoveCountKey(size)] = preferences[legacyMoveCountKey] ?: 0
            }
            preferences.remove(legacyBoardKey)
            preferences.remove(legacyBoardRowsKey)
            preferences.remove(legacyBoardColsKey)
            preferences.remove(legacyCurrentScoreKey)
            preferences.remove(legacyHasWonKey)
            preferences.remove(legacyContinueKey)
            preferences.remove(legacyMoveCountKey)
        }
    }

    /**
     * Wipes the player's personal data from the device: every per-size best, every per-size saved
     * game, any legacy save, and the chosen nickname. UI preferences (sound/haptics/move
     * counter/selected size) are kept. Called when a user deletes their account.
     */
    suspend fun clearLocalData() {
        context.dataStore.edit { preferences ->
            GridSize.entries.forEach { size ->
                preferences.remove(bestScoreKey(size))
                preferences.remove(boardKey(size))
                preferences.remove(savedScoreKey(size))
                preferences.remove(savedHasWonKey(size))
                preferences.remove(savedContinueKey(size))
                preferences.remove(savedMoveCountKey(size))
            }
            preferences.remove(legacyBestScoreKey)
            preferences.remove(legacyBoardKey)
            preferences.remove(legacyBoardRowsKey)
            preferences.remove(legacyBoardColsKey)
            preferences.remove(legacyCurrentScoreKey)
            preferences.remove(legacyHasWonKey)
            preferences.remove(legacyContinueKey)
            preferences.remove(legacyMoveCountKey)
            preferences.remove(playerNameKey)
        }
    }

    /** Encodes the grid as row-major, comma-separated values (0 = empty cell). */
    private fun encode(grid: List<List<Int>>): String =
        grid.flatten().joinToString(separator = ",")

    /**
     * Decodes a row-major, comma-separated value string back into a [rows] x [cols] grid. Returns
     * null if the data is malformed (wrong cell count or non-numeric), so a corrupt save degrades
     * gracefully to "start a new game".
     */
    private fun decode(encoded: String, rows: Int, cols: Int): List<List<Int>>? {
        val values = encoded.split(",").mapNotNull { it.toIntOrNull() }
        if (values.size != rows * cols) return null
        return values.chunked(cols)
    }
}
