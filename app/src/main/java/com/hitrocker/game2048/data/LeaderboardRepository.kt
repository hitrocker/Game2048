package com.hitrocker.game2048.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hitrocker.game2048.game.GridSize
import kotlinx.coroutines.tasks.await

/** One row of a leaderboard: a player's best score for a given board size. */
data class LeaderboardEntry(
    val uid: String,
    val name: String,
    val score: Int
)

/**
 * Reads and writes per-size global leaderboards in Cloud Firestore.
 *
 * Layout: `leaderboards/{sizeLabel}/entries/{uid}`, one document per player holding their best
 * score for that size. Storing by uid means a player only ever occupies a single row per board,
 * which is overwritten whenever they beat their own record.
 */
class LeaderboardRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun entries(size: GridSize) =
        db.collection("leaderboards").document(size.label).collection("entries")

    /** Upserts the player's best [score] for [size] under their [uid]. */
    suspend fun submitScore(size: GridSize, uid: String, name: String, score: Int) {
        entries(size).document(uid).set(
            mapOf(
                "name" to name.take(20),
                "bestScore" to score,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** Deletes the player's entry for [size] (used when a user deletes their account). */
    suspend fun deleteEntry(size: GridSize, uid: String) {
        entries(size).document(uid).delete().await()
    }

    /** Fetches the top [limit] scores for [size], highest first. */
    suspend fun topScores(size: GridSize, limit: Long = 50): List<LeaderboardEntry> {
        val snapshot = entries(size)
            .orderBy("bestScore", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        return snapshot.documents.map { doc ->
            LeaderboardEntry(
                uid = doc.id,
                name = doc.getString("name") ?: "Guest",
                score = (doc.getLong("bestScore") ?: 0L).toInt()
            )
        }
    }
}
