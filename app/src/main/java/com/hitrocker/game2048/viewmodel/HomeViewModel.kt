package com.hitrocker.game2048.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hitrocker.game2048.data.PreferencesManager
import com.hitrocker.game2048.game.GridSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the Home screen and Settings sheet. Reads everything from the shared
 * [PreferencesManager] (same DataStore the game uses), so the best score and the sound/haptics
 * toggles stay in sync with what happens in-game.
 *
 * Each value is exposed as a [StateFlow] derived from the underlying preference Flow via [stateIn],
 * so the UI gets a stable, lifecycle-aware snapshot and never recomposes from a cold Flow.
 */
class HomeViewModel(private val preferencesManager: PreferencesManager) : ViewModel() {

    /** The board size currently highlighted in the picker. */
    val selectedSize: StateFlow<GridSize> = preferencesManager.selectedSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridSize.default)

    /** Best score for the currently selected size, so the picker can show it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val bestScore: StateFlow<Int> = preferencesManager.selectedSizeFlow
        .flatMapLatest { preferencesManager.bestScoreFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setSelectedSize(size: GridSize) {
        viewModelScope.launch { preferencesManager.setSelectedSize(size) }
    }

    val soundEnabled: StateFlow<Boolean> = preferencesManager.soundEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val hapticsEnabled: StateFlow<Boolean> = preferencesManager.hapticsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val showMoveCounter: StateFlow<Boolean> = preferencesManager.showMoveCounterFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val animationsEnabled: StateFlow<Boolean> = preferencesManager.animationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Whether the player has already seen the how-to-play guide. Starts as null ("not yet known")
     * so the Home screen can tell "definitely a first-time player" (false) apart from "still loading
     * from disk" (null) and avoid flashing the guide at returning players.
     */
    val hasSeenHowToPlay: StateFlow<Boolean?> = preferencesManager.hasSeenHowToPlayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSoundEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setHapticsEnabled(enabled) }
    }

    fun setShowMoveCounter(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setShowMoveCounter(enabled) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAnimationsEnabled(enabled) }
    }

    /** Marks the how-to-play guide as seen so it won't auto-show again. */
    fun markHowToPlaySeen() {
        viewModelScope.launch { preferencesManager.setHasSeenHowToPlay() }
    }
}

/**
 * Manual factory, mirroring [GameViewModelFactory], since the project intentionally uses no DI
 * framework.
 */
class HomeViewModelFactory(private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(preferencesManager) as T
    }
}
