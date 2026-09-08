package com.hitrocker.game2048.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hitrocker.game2048.ui.theme.DangerRed
import com.hitrocker.game2048.ui.theme.PlayAccent

/**
 * Material 3 modal bottom sheet with the app's settings. Stateless: the caller passes the current
 * toggle values and change callbacks, so the same sheet is reused from both the Home screen and the
 * in-game header. Values are backed by DataStore, so changes persist immediately and across restarts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    showMoveCounter: Boolean,
    animationsEnabled: Boolean,
    accountName: String,
    isSignedIn: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onShowMoveCounterChange: (Boolean) -> Unit,
    onAnimationsEnabledChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            AccountRow(
                accountName = accountName,
                isSignedIn = isSignedIn,
                onSignIn = onSignIn,
                onSignOut = onSignOut
            )

            SettingToggleRow(
                title = "Sound Effects",
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange
            )

            SettingToggleRow(
                title = "Haptic Feedback",
                checked = hapticsEnabled,
                onCheckedChange = onHapticsEnabledChange
            )

            SettingToggleRow(
                title = "Move Counter",
                checked = showMoveCounter,
                onCheckedChange = onShowMoveCounterChange
            )

            SettingToggleRow(
                title = "Smooth Animations",
                checked = animationsEnabled,
                onCheckedChange = onAnimationsEnabledChange
            )

            // Account deletion is required by Google Play for apps with account creation; only shown
            // to signed-in (email/Google) users since guests have no real account to delete.
            if (isSignedIn) {
                TextButton(
                    onClick = onDeleteAccount,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Delete account",
                        color = DangerRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Account card at the top of the settings sheet: shows who you're playing as and a Sign in / Sign
 * out action. Signing in upgrades the anonymous guest to a real (email/Google) identity so scores
 * are saved to the global leaderboard under your name.
 */
@Composable
private fun AccountRow(
    accountName: String,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSignedIn) "Signed in" else "Playing as guest",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = if (isSignedIn) onSignOut else onSignIn) {
                Text(
                    text = if (isSignedIn) "Sign out" else "Sign in",
                    color = PlayAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * A single rounded settings row: a title on the left and a [Switch] on the right. Kept private and
 * reusable so the settings don't duplicate layout code.
 */
@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
