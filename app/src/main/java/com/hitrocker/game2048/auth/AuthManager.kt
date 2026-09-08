package com.hitrocker.game2048.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Snapshot of the current Firebase user, exposed to the UI. A player is always signed in to
 * *something* (anonymous by default); [signedIn] is true only once they upgrade to a real
 * email/Google account.
 */
data class AuthState(
    val isAnonymous: Boolean = true,
    val signedIn: Boolean = false,
    val uid: String? = null,
    val displayName: String? = null
)

/**
 * Thin wrapper over [FirebaseAuth]. The app keeps every player signed in (anonymously by default)
 * so scores can always be submitted; signing in with email or Google is optional and just attaches
 * a real identity/name.
 *
 * All sign-in UI is now our own Compose screens, so this only does the Firebase calls. The Google
 * ID token itself is obtained via Credential Manager (see [GoogleIdTokenProvider]) and handed to
 * [signInWithGoogleIdToken].
 *
 * This class is deliberately free of any app/game dependencies so it can be reused as-is in other
 * projects.
 */
class AuthManager {

    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow(snapshot())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        auth.addAuthStateListener { _authState.value = snapshot() }
    }

    private fun snapshot(): AuthState {
        val user = auth.currentUser
        return AuthState(
            isAnonymous = user?.isAnonymous ?: true,
            signedIn = user != null && user.isAnonymous == false,
            uid = user?.uid,
            displayName = user?.displayName
        )
    }

    /** Signs in anonymously if there is no current user yet (silent guest mode on first launch). */
    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            runCatching { auth.signInAnonymously().await() }
        }
    }

    fun currentUid(): String? = auth.currentUser?.uid

    /**
     * The name to show on the leaderboard. A signed-in account's provider display name (Google /
     * email) always wins so it matches the "Playing as" header; guests (no display name) fall back
     * to their explicit [stored] nickname, then "Guest".
     */
    fun resolveName(stored: String?): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: stored?.takeIf { it.isNotBlank() }
            ?: "Guest"

    /** Logs into an existing email/password account. */
    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    /** Creates a new email/password account and sets its display [name]. */
    suspend fun createAccount(email: String, password: String, name: String): Result<Unit> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            result.user?.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
            )?.await()
            // Re-emit so the new display name is reflected immediately.
            _authState.value = snapshot()
            Unit
        }

    /** Sends a password-reset email. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    /** Completes Google sign-in using an ID token obtained via Credential Manager. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        Unit
    }

    /**
     * Signs out of the real account and immediately drops back to a fresh anonymous session, so the
     * player can keep playing and submitting scores as a guest afterwards.
     */
    fun signOut() {
        auth.signOut()
        auth.signInAnonymously()
    }

    /**
     * The sign-in provider of the current real account: "google.com", "password" (email), or null
     * for an anonymous guest. Used to pick the right re-authentication path before deletion.
     */
    fun currentProviderId(): String? =
        auth.currentUser?.providerData
            ?.map { it.providerId }
            ?.firstOrNull { it != "firebase" }

    /** Re-authenticates the current user with a fresh Google ID token (required before deletion). */
    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    /** Re-authenticates the current email user with their password (required before deletion). */
    suspend fun reauthenticateWithEmail(password: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val email = user.email ?: error("No email on account")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        Unit
    }

    /**
     * Permanently deletes the current Firebase user, then immediately starts a fresh anonymous
     * session so the app keeps working as a guest. The caller must re-authenticate first.
     */
    suspend fun deleteCurrentUser(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        user.delete().await()
        auth.signInAnonymously().await()
        Unit
    }
}
