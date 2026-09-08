package com.hitrocker.game2048.auth

/**
 * App-specific cleanup that runs when an account is deleted, given the deleted user's [uid].
 *
 * Implement one per data source (e.g. remote leaderboard rows, local storage) and hand the list to
 * [AccountDeleter]. Keeping this as an interface is what lets the deletion flow stay reusable: the
 * generic flow doesn't know *what* gets cleaned up, only that it should be.
 */
fun interface AccountDataCleaner {
    suspend fun onAccountDeleted(uid: String)
}
