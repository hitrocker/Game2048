package com.hitrocker.game2048.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Thin wrapper around the system [Vibrator] that gives each notable game event a distinct, short
 * haptic "feel". Distinct patterns (a soft tick for merges, a double-buzz for winning, a longer
 * buzz for game over) are what make the game feel tactile and responsive in the hand.
 *
 * Safe to construct and call on any device: if there is no vibrator (or the user disables haptics
 * via [enabled]), every method simply no-ops.
 */
class HapticManager(context: Context) {

    /** Lets a future settings toggle silence haptics without the call sites needing to care. */
    var enabled: Boolean = true

    // Resolving the Vibrator differs by API level: API 31+ goes through VibratorManager, while
    // older devices use the (now-deprecated) direct system service.
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** A short, light tick played whenever tiles merge. */
    fun merge() = oneShot(durationMs = 18, amplitude = 70)

    /** A celebratory double-buzz played the first time a 2048 tile is created. */
    fun win() = waveform(longArrayOf(0, 40, 70, 40, 70, 90))

    /** A single longer buzz played when the board fills up with no moves left. */
    fun gameOver() = oneShot(durationMs = 120, amplitude = VibrationEffect.DEFAULT_AMPLITUDE)

    private fun oneShot(durationMs: Long, amplitude: Int) {
        val v = activeVibrator() ?: return
        v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    }

    private fun waveform(timings: LongArray) {
        val v = activeVibrator() ?: return
        v.vibrate(VibrationEffect.createWaveform(timings, -1)) // -1 = do not repeat.
    }

    /** Returns the vibrator only if haptics are enabled and the device can actually vibrate. */
    private fun activeVibrator(): Vibrator? {
        if (!enabled) return null
        val v = vibrator ?: return null
        return if (v.hasVibrator()) v else null
    }
}
