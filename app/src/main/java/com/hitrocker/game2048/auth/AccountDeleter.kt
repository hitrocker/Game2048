package com.hitrocker.game2048.auth

import kotlinx.coroutines.CancellationException

/**
 * Reusable account-deletion orchestrator. Runs the required steps in order:
 *  1. re-authenticate (provider-specific; supplied by the caller as [reauth], since Firebase
 *     requires a recent login before deletion),
 *  2. run every [AccountDataCleaner] (best-effort — a failing cleaner won't abort the deletion),
 *  3. delete the Firebase user (which drops back to a fresh anonymous session).
 *
 * It knows nothing about the app's data model — the caller injects the [cleaners] and the re-auth
 * step — so it can be reused across projects unchanged.
 */
class AccountDeleter(
    private val authManager: AuthManager,
    private val cleaners: List<AccountDataCleaner>
) {

    /**
     * Deletes the current account. [reauth] performs the provider-specific re-authentication and
     * returns its [Result]; if it fails (or throws), nothing is deleted. Coroutine cancellation is
     * propagated rather than swallowed.
     */
    suspend fun delete(reauth: suspend () -> Result<Unit>): Result<Unit> {
        val uid = authManager.currentUid()
            ?: return Result.failure(IllegalStateException("Not signed in."))

        val reauthResult = try {
            reauth()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
        reauthResult.onFailure { return Result.failure(it) }

        cleaners.forEach { cleaner -> runCatching { cleaner.onAccountDeleted(uid) } }

        return authManager.deleteCurrentUser()
    }
}
