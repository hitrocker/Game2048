package com.hitrocker.game2048

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hitrocker.game2048.auth.AccountDataCleaner
import com.hitrocker.game2048.auth.AccountDeleter
import com.hitrocker.game2048.auth.AuthManager
import com.hitrocker.game2048.auth.GoogleIdTokenProvider
import com.hitrocker.game2048.data.LeaderboardRepository
import com.hitrocker.game2048.data.PreferencesManager
import com.hitrocker.game2048.game.GridSize
import com.hitrocker.game2048.ui.screens.AuthScreen
import com.hitrocker.game2048.ui.screens.GameScreen
import com.hitrocker.game2048.ui.screens.HomeScreen
import com.hitrocker.game2048.ui.screens.LeaderboardScreen
import com.hitrocker.game2048.ui.theme.DangerRed
import com.hitrocker.game2048.ui.theme.Game2048Theme
import com.hitrocker.game2048.viewmodel.AuthViewModel
import com.hitrocker.game2048.viewmodel.AuthViewModelFactory
import com.hitrocker.game2048.viewmodel.GameViewModel
import com.hitrocker.game2048.viewmodel.GameViewModelFactory
import com.hitrocker.game2048.viewmodel.HomeViewModel
import com.hitrocker.game2048.viewmodel.HomeViewModelFactory
import com.hitrocker.game2048.viewmodel.LeaderboardViewModel
import com.hitrocker.game2048.viewmodel.LeaderboardViewModelFactory
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val GAME = "game"
    const val LEADERBOARD = "leaderboard"
    const val AUTH = "auth"
}

class MainActivity : ComponentActivity() {

    // A single PreferencesManager (app-wide DataStore) shared by every ViewModel, so the best score
    // and the sound/haptics settings stay consistent between the Home screen and the game.
    private val preferencesManager by lazy { PreferencesManager(applicationContext) }

    // Firebase plumbing for the global leaderboards: one auth wrapper and one Firestore repository,
    // shared by the game (submits scores) and the leaderboard screen (reads/signs in).
    private val authManager by lazy { AuthManager() }
    private val leaderboardRepository by lazy { LeaderboardRepository() }

    // Reusable auth helpers: Google ID token retrieval (Credential Manager) and the generic
    // account-deletion flow. The game-specific cleanup that runs on deletion (leaderboard rows +
    // local data) is injected here as cleaners, keeping the auth package free of game knowledge.
    private val googleIdTokenProvider by lazy {
        GoogleIdTokenProvider(getString(R.string.default_web_client_id))
    }
    private val accountDeleter by lazy {
        AccountDeleter(
            authManager = authManager,
            cleaners = listOf(
                AccountDataCleaner { uid ->
                    GridSize.entries.forEach { size ->
                        runCatching { leaderboardRepository.deleteEntry(size, uid) }
                    }
                },
                AccountDataCleaner { preferencesManager.clearLocalData() }
            )
        )
    }

    // ViewModels survive configuration changes via `by viewModels`; their distinct classes mean the
    // default keys don't collide.
    private val gameViewModel: GameViewModel by viewModels {
        GameViewModelFactory(preferencesManager, leaderboardRepository, authManager)
    }
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(preferencesManager)
    }
    private val leaderboardViewModel: LeaderboardViewModel by viewModels {
        LeaderboardViewModelFactory(preferencesManager, leaderboardRepository, authManager)
    }
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(
            authManager,
            preferencesManager,
            leaderboardRepository,
            googleIdTokenProvider,
            accountDeleter
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        // Silently sign the player in as an anonymous guest so scores can be submitted from the
        // very first game; upgrading to email/Google later is optional.
        lifecycleScope.launch { authManager.ensureSignedIn() }
        setContent {
            Game2048App(
                gameViewModel = gameViewModel,
                homeViewModel = homeViewModel,
                leaderboardViewModel = leaderboardViewModel,
                authViewModel = authViewModel
            )
        }
    }

    // Re-hide the bars whenever the window regains focus (they reappear after a swipe, after a
    // dialog/sheet, or when returning from the background), keeping the game full-screen.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Sticky immersive full-screen: hides the status and navigation bars; a swipe from the edge
     * reveals them transiently, then they auto-hide again.
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
private fun Game2048App(
    gameViewModel: GameViewModel,
    homeViewModel: HomeViewModel,
    leaderboardViewModel: LeaderboardViewModel,
    authViewModel: AuthViewModel
) {
    Game2048Theme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()

            // Account info, shared by the settings sheet on both Home and Game. A signed-in user shows
            // their provider display name; a guest shows their chosen nickname (or "Guest").
            val authState by leaderboardViewModel.authState.collectAsState()
            val storedName by leaderboardViewModel.playerName.collectAsState()
            val accountName = if (authState.signedIn) {
                authState.displayName?.takeIf { it.isNotBlank() } ?: "Player"
            } else {
                storedName.takeIf { it.isNotBlank() } ?: "Guest"
            }
            val onSignIn = { navController.navigate(Routes.AUTH) }
            val onSignOut = { leaderboardViewModel.signOut() }

            var showDeleteAccount by remember { mutableStateOf(false) }
            val onDeleteAccount = { showDeleteAccount = true }

            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onPlay = { size ->
                            gameViewModel.playSize(size)
                            navController.navigate(Routes.GAME)
                        },
                        onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                        viewModel = homeViewModel,
                        accountName = accountName,
                        isSignedIn = authState.signedIn,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        onDeleteAccount = onDeleteAccount
                    )
                }
                composable(Routes.GAME) {
                    GameScreen(
                        viewModel = gameViewModel,
                        accountName = accountName,
                        isSignedIn = authState.signedIn,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        onDeleteAccount = onDeleteAccount,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.LEADERBOARD) {
                    LeaderboardScreen(
                        viewModel = leaderboardViewModel,
                        onBack = { navController.popBackStack() },
                        onSignIn = { navController.navigate(Routes.AUTH) }
                    )
                }
                composable(Routes.AUTH) {
                    AuthScreen(
                        viewModel = authViewModel,
                        onBack = { navController.popBackStack() },
                        onAuthComplete = { navController.popBackStack() }
                    )
                }
            }

            if (showDeleteAccount) {
                DeleteAccountDialog(
                    authViewModel = authViewModel,
                    onClose = {
                        authViewModel.resetDeleteState()
                        showDeleteAccount = false
                    }
                )
            }
        }
    }
}

/**
 * Confirmation + (for email accounts) password prompt for permanently deleting the account. Drives
 * [AuthViewModel.deleteAccount]; on success the user is dropped back to an anonymous guest and the
 * dialog closes.
 */
@Composable
private fun DeleteAccountDialog(
    authViewModel: AuthViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val deleteState by authViewModel.deleteState.collectAsState()
    val requiresPassword = remember { authViewModel.deleteRequiresPassword() }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authViewModel.accountDeleted.collect {
            Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!deleteState.loading) onClose() },
        title = { Text("Delete account?") },
        text = {
            Column {
                Text(
                    "This permanently deletes your account and removes your scores from the " +
                        "leaderboard. This can't be undone."
                )
                if (requiresPassword) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Confirm your password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                deleteState.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DangerRed)
                }
                if (deleteState.loading) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(
                        color = DangerRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !deleteState.loading && (!requiresPassword || password.isNotBlank()),
                onClick = { authViewModel.deleteAccount(context, password.ifBlank { null }) }
            ) {
                Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !deleteState.loading, onClick = onClose) {
                Text("Cancel")
            }
        }
    )
}
