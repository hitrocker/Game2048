package com.hitrocker.game2048.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hitrocker.game2048.ui.theme.PlayAccent

/**
 * A short, friendly rules explainer shown from the Settings sheet (on both Home and in-game).
 * Stateless: the caller owns the visibility flag and passes [onDismiss].
 */
@Composable
fun HowToPlayDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "How to play", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HowToStep("Swipe up, down, left, or right to slide all the tiles.")
                HowToStep("When two tiles with the same number touch, they merge into one.")
                HowToStep("A new tile appears after every move.")
                HowToStep("Reach the target tile to win - then keep going for a higher score!")
                HowToStep("The game ends when the board is full and no moves are left.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Got it", color = PlayAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun HowToStep(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = PlayAccent
        )
        Spacer(Modifier.width(0.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
