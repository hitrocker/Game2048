package com.hitrocker.game2048.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hitrocker.game2048.auth.AuthManager
import com.hitrocker.game2048.auth.AuthState
import com.hitrocker.game2048.data.LeaderboardEntry
import com.hitrocker.game2048.data.LeaderboardRepository
import com.hitrocker.game2048.data.PreferencesManager
import com.hitrocker.game2048.game.GridSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the leaderboard screen. */
data class LeaderboardUiState(
    val size: GridSize = GridSize.default,
    val entries: List<LeaderboardEntry> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false
)

/**
 * Drives the leaderboard screen: loads the top scores for the chosen size, lets the player switch
 * sizes, and surfaces auth/name controls. After a sign-in or name change it re-publishes the
 * player's local bests so their records show up under the right identity and name.
 */
class LeaderboardViewModel(
    private val preferencesManager: PreferencesManager,
    private val leaderboardRepository: LeaderboardRepository,
    val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    val authState: StateFlow<AuthState> = authManager.authState

    val playerName: StateFlow<String> = preferencesManager.playerNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        viewModelScope.launch {
            val size = preferencesManager.selectedSizeFlow.first()
            load(size)
        }
    }

    /** The uid of the local player, used to highlight their row in the list. */
    fun currentUid(): String? = authManager.currentUid()

    /** Loads (or reloads) the leaderboard for [size]. */
    fun load(size: GridSize = _uiState.value.size) {
        viewModelScope.launch {
            _uiState.update { it.copy(size = size, loading = true, error = false) }
            val result = runCatching { leaderboardRepository.topScores(size) }
            _uiState.update { current ->
                result.fold(
                    onSuccess = { current.copy(entries = it, loading = false) },
                    onFailure = { current.copy(loading = false, error = true) }
                )
            }
        }
    }

    fun selectSize(size: GridSize) = load(size)

    /** Persists a new nickname and re-publishes the player's bests under it. */
    fun setPlayerName(name: String) {
        viewModelScope.launch {
            preferencesManager.setPlayerName(name)
            resubmitLocalBests()
            load()
        }
    }

    /** Called after FirebaseUI returns a successful sign-in. */
    fun onSignedIn() {
        viewModelScope.launch {
            resubmitLocalBests()
            load()
        }
    }

    fun signOut() {
        authManager.signOut()
    }

    /**
     * Pushes every non-zero local best to the leaderboard under the current uid/name. Safe to call
     * repeatedly; each call simply overwrites the player's own rows.
     */
    private suspend fun resubmitLocalBests() {
        val uid = authManager.currentUid() ?: return
        val name = authManager.resolveName(preferencesManager.playerNameFlow.first())
        preferencesManager.allBestScores().forEach { (size, score) ->
            if (score > 0) {
                runCatching { leaderboardRepository.submitScore(size, uid, name, score) }
            }
        }
    }
}

/** Manual factory, matching the project's no-DI convention. */
class LeaderboardViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val leaderboardRepository: LeaderboardRepository,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LeaderboardViewModel(preferencesManager, leaderboardRepository, authManager) as T
    }
}
