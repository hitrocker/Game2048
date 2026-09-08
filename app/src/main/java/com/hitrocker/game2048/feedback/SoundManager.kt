package com.hitrocker.game2048.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.IOException

/**
 * Plays short sound effects for game events via [SoundPool] (which is purpose-built for small,
 * low-latency clips like UI/game blips).
 *
 * Sounds are loaded from the app's `assets/sounds/` folder *by file name* rather than from
 * `res/raw`. The practical benefit: the project compiles and runs with no audio files present at
 * all - any missing clip simply loads as `null` and its play call no-ops. Drop the matching files
 * into `app/src/main/assets/sounds/` to enable them, no code change required.
 *
 * Expected (all optional): `move.wav`, `merge.wav`, `win.wav`, `game_over.wav`.
 */
class SoundManager(context: Context) {

    /** Lets a future settings toggle mute sound without the call sites needing to care. */
    var enabled: Boolean = true

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val moveSound = load(context, "sounds/move.wav")
    private val mergeSound = load(context, "sounds/merge.wav")
    private val winSound = load(context, "sounds/win.wav")
    private val gameOverSound = load(context, "sounds/game_over.wav")

    fun move() = play(moveSound)
    fun merge() = play(mergeSound)
    fun win() = play(winSound)
    fun gameOver() = play(gameOverSound)

    /** Must be called when the owner is destroyed to free the underlying native resources. */
    fun release() = soundPool.release()

    /** Returns the loaded sound id, or null if the asset file isn't present (sound is optional). */
    private fun load(context: Context, assetPath: String): Int? = try {
        context.assets.openFd(assetPath).use { descriptor ->
            soundPool.load(descriptor, 1)
        }
    } catch (e: IOException) {
        Log.i(TAG, "Optional sound asset not found, skipping: $assetPath")
        null
    }

    private fun play(soundId: Int?) {
        if (!enabled) return
        val id = soundId ?: return
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    private companion object {
        const val TAG = "SoundManager"
    }
}
