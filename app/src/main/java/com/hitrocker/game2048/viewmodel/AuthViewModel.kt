package com.hitrocker.game2048.viewmodel

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hitrocker.game2048.auth.AccountDeleter
import com.hitrocker.game2048.auth.AuthManager
import com.hitrocker.game2048.auth.GoogleIdTokenProvider
import com.hitrocker.game2048.data.LeaderboardRepository
import com.hitrocker.game2048.data.PreferencesManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient state for the custom sign-in screen. */
data class AuthFormState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

/** Transient state for the account-deletion flow (shown from the settings sheet). */
data class DeleteAccountState(
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * Backs the custom (in-app) sign-in screen. Performs email/password auth and Google sign-in (the
 * Google ID token is fetched via Credential Manager here, then exchanged for a Firebase credential
 * by [AuthManager]). On any successful sign-in it re-publishes the player's local bests so they
 * appear under the new identity, then emits [signedIn] so the UI can navigate back.
 */
class AuthViewModel(
    private val authManager: AuthManager,
    private val preferencesManager: PreferencesManager,
    private val leaderboardRepository: LeaderboardRepository,
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val accountDeleter: AccountDeleter
) : ViewModel() {

    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    private val _signedIn = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signedIn: SharedFlow<Unit> = _signedIn.asSharedFlow()

    private val _deleteState = MutableStateFlow(DeleteAccountState())
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    private val _accountDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val accountDeleted: SharedFlow<Unit> = _accountDeleted.asSharedFlow()

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun signIn(email: String, password: String) {
        val validation = validate(email, password, null)
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }
        run { authManager.signInWithEmail(email, password) }
    }

    fun createAccount(email: String, password: String, name: String) {
        val validation = validate(email, password, name)
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }
        run { authManager.createAccount(email, password, name) }
    }

    fun resetPassword(email: String) {
        if (!email.contains("@")) {
            _state.update { it.copy(error = "Enter your email first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            authManager.sendPasswordReset(email).fold(
                onSuccess = { _state.update { it.copy(loading = false, info = "Reset email sent.") } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
            )
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            try {
                val token = googleIdTokenProvider.fetch(context)
                authManager.signInWithGoogleIdToken(token).fold(
                    onSuccess = { onSignedIn() },
                    onFailure = { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
                )
            } catch (e: GetCredentialCancellationException) {
                _state.update { it.copy(loading = false) } // user dismissed the picker
            } catch (e: NoCredentialException) {
                _state.update { it.copy(loading = false, error = "No Google account found on this device.") }
            } catch (e: GetCredentialException) {
                _state.update { it.copy(loading = false, error = "Google sign-in failed. Try again.") }
            }
        }
    }

    /** True if the signed-in account uses email/password and therefore needs a password to delete. */
    fun deleteRequiresPassword(): Boolean = authManager.currentProviderId() == "password"

    fun resetDeleteState() = _deleteState.update { DeleteAccountState() }

    /**
     * Permanently deletes the current account. The generic flow (re-auth -> data cleanup -> delete
     * user -> back to anonymous guest) lives in [AccountDeleter]; here we only supply the
     * provider-specific re-authentication step (Google via Credential Manager, or the provided
     * [password] for email accounts) and map the outcome to UI state. The actual data cleanup
     * (leaderboard rows, local data) is performed by the [AccountDeleter]'s injected cleaners.
     */
    fun deleteAccount(context: Context, password: String?) {
        viewModelScope.launch {
            _deleteState.update { it.copy(loading = true, error = null) }

            val provider = authManager.currentProviderId()
            val result = accountDeleter.delete {
                when (provider) {
                    "google.com" ->
                        authManager.reauthenticateWithGoogle(googleIdTokenProvider.fetch(context))
                    "password" ->
                        if (password.isNullOrBlank()) {
                            Result.failure(Exception("Enter your password to confirm."))
                        } else {
                            authManager.reauthenticateWithEmail(password)
                        }
                    else -> Result.success(Unit)
                }
            }

            result.fold(
                onSuccess = {
                    _deleteState.update { DeleteAccountState() }
                    _accountDeleted.tryEmit(Unit)
                },
                onFailure = { e ->
                    when (e) {
                        // User dismissed the Google account picker — quietly stop, no error.
                        is GetCredentialCancellationException ->
                            _deleteState.update { it.copy(loading = false) }
                        is GetCredentialException ->
                            _deleteState.update {
                                it.copy(loading = false, error = "Couldn't verify your Google account. Try again.")
                            }
                        else ->
                            _deleteState.update { it.copy(loading = false, error = friendly(e)) }
                    }
                }
            )
        }
    }

    /** Shared completion path for email/create flows. */
    private fun run(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            action().fold(
                onSuccess = { onSignedIn() },
                onFailure = { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
            )
        }
    }

    private suspend fun onSignedIn() {
        resubmitLocalBests()
        _state.update { it.copy(loading = false) }
        _signedIn.tryEmit(Unit)
    }

    private suspend fun resubmitLocalBests() {
        val uid = authManager.currentUid() ?: return
        val name = authManager.resolveName(preferencesManager.playerNameFlow.first())
        preferencesManager.allBestScores().forEach { (size, score) ->
            if (score > 0) {
                runCatching { leaderboardRepository.submitScore(size, uid, name, score) }
            }
        }
    }

    private fun validate(email: String, password: String, name: String?): String? = when {
        !email.contains("@") || email.length < 3 -> "Enter a valid email."
        password.length < 6 -> "Password must be at least 6 characters."
        name != null && name.isBlank() -> "Enter your name."
        else -> null
    }

    private fun friendly(e: Throwable): String = e.localizedMessage ?: "Something went wrong."
}

/** Manual factory, matching the project's no-DI convention. */
class AuthViewModelFactory(
    private val authManager: AuthManager,
    private val preferencesManager: PreferencesManager,
    private val leaderboardRepository: LeaderboardRepository,
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val accountDeleter: AccountDeleter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(
            authManager,
            preferencesManager,
            leaderboardRepository,
            googleIdTokenProvider,
            accountDeleter
        ) as T
    }
}
