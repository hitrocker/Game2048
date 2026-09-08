package com.hitrocker.game2048.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitrocker.game2048.R
import com.hitrocker.game2048.feedback.HapticManager
import com.hitrocker.game2048.feedback.SoundManager
import com.hitrocker.game2048.ui.components.GameBoard
import com.hitrocker.game2048.ui.components.ScoreCard
import com.hitrocker.game2048.ui.components.SettingsSheet
import com.hitrocker.game2048.ui.components.detectSwipeGesture
import com.hitrocker.game2048.ui.theme.TileTextDark
import com.hitrocker.game2048.viewmodel.GameEvent
import com.hitrocker.game2048.viewmodel.GameUiState
import com.hitrocker.game2048.viewmodel.GameViewModel

/**
 * The single screen of the app: header (title + score cards), short instructions, the
 * animated board itself, win/game-over overlays, and a restart button.
 *
 * This composable is intentionally "dumb" - it only reads [GameViewModel.uiState] and forwards
 * user intent (swipes, button taps) back to the ViewModel. No game logic lives here.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    accountName: String,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pressing system back from the game returns to the Home screen.
    BackHandler(onBack = onBack)

    // Feedback managers are tied to this screen's lifetime. SoundManager owns native resources, so
    // it must be released when the screen leaves composition.
    val context = LocalContext.current
    val haptics = remember { HapticManager(context) }
    val soundManager = remember { SoundManager(context) }
    DisposableEffect(soundManager) {
        onDispose { soundManager.release() }
    }

    // Mirror the persisted preferences onto the managers so disabling sound/haptics in Settings
    // immediately silences them. Assigning each recomposition is cheap (plain Boolean flags).
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val showMoveCounter by viewModel.showMoveCounter.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    soundManager.enabled = soundEnabled
    haptics.enabled = hapticsEnabled

    var showSettings by remember { mutableStateOf(false) }
    var showNewGameConfirm by remember { mutableStateOf(false) }

    // Turn one-shot game events into haptics + sound. Collected here (not in the ViewModel) so the
    // Android-specific feedback stays out of the platform-agnostic ViewModel.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GameEvent.MOVE -> soundManager.move()
                GameEvent.MERGE -> {
                    soundManager.merge()
                    haptics.merge()
                }
                GameEvent.WIN -> {
                    soundManager.win()
                    haptics.win()
                }
                GameEvent.GAME_OVER -> {
                    soundManager.gameOver()
                    haptics.gameOver()
                }
            }
        }
    }

    // Swipe is detected at the whole-screen level so the player can swipe anywhere (header, empty
    // space, the board) rather than only inside the board square. Taps on buttons still work since
    // detectDragGestures only consumes drags.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectSwipeGesture(viewModel::onSwipe) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

        // Top bar: menu (settings) on the left, the "2048 CLASSIC" title centered, and Home +
        // Restart actions on the right. A Box lets the title stay screen-centered regardless of how
        // wide the icon clusters on either side are.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.settings_label),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = viewModel::undo, enabled = uiState.canUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.undo_label),
                        tint = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (uiState.canUndo) 1f else 0.3f
                        )
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                color = TileTextDark,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )

            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = stringResource(R.string.home_label),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = {
                        // Nothing to lose once the game is over - restart immediately. Otherwise ask
                        // first so an accidental tap doesn't wipe an in-progress game.
                        if (uiState.isGameOver) {
                            viewModel.startNewGame()
                        } else {
                            showNewGameConfirm = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.restart_button),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScoreCard(
                label = stringResource(R.string.score_label),
                value = uiState.score,
                modifier = Modifier.weight(1f)
            )
            ScoreCard(
                label = stringResource(R.string.best_label),
                value = uiState.bestScore,
                modifier = Modifier.weight(1f)
            )
        }

        // Fill the leftover space and center the board exactly within it. The board keeps its
        // size's aspect ratio and is shrunk to fit whichever of width/height is the binding
        // constraint. The move counter is positioned just below the board (and at its left edge) via
        // an offset, so it hugs the grid without affecting the board's centering.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Reserve a little vertical room for the move counter so tall boards (e.g. 2x4, 5x7)
            // don't consume the full height and clip it.
            val counterAllowance = if (showMoveCounter) 28.dp else 0.dp
            val availableHeight = maxHeight - counterAllowance
            val aspect = uiState.cols.toFloat() / uiState.rows.toFloat()
            val boardWidth =
                if (maxWidth / aspect <= availableHeight) maxWidth else availableHeight * aspect
            val boardHeight = boardWidth / aspect
            val boardTop = (availableHeight - boardHeight) / 2
            val boardLeft = (maxWidth - boardWidth) / 2

            BoardWithOverlays(
                uiState = uiState,
                animate = animationsEnabled,
                onRestart = viewModel::startNewGame,
                onContinue = viewModel::dismissWinDialogAndContinue,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = boardLeft, y = boardTop)
                    .width(boardWidth)
            )

            if (showMoveCounter) {
                Text(
                    text = "${uiState.moveCount} moves",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = boardLeft, y = boardTop + boardHeight + 8.dp)
                )
            }
        }
        }
    }

    if (showSettings) {
        SettingsSheet(
            soundEnabled = soundEnabled,
            hapticsEnabled = hapticsEnabled,
            showMoveCounter = showMoveCounter,
            animationsEnabled = animationsEnabled,
            accountName = accountName,
            isSignedIn = isSignedIn,
            onSoundEnabledChange = viewModel::setSoundEnabled,
            onHapticsEnabledChange = viewModel::setHapticsEnabled,
            onShowMoveCounterChange = viewModel::setShowMoveCounter,
            onAnimationsEnabledChange = viewModel::setAnimationsEnabled,
            onSignIn = { showSettings = false; onSignIn() },
            onSignOut = onSignOut,
            onDeleteAccount = { showSettings = false; onDeleteAccount() },
            onDismiss = { showSettings = false }
        )
    }

    if (showNewGameConfirm) {
        AlertDialog(
            onDismissRequest = { showNewGameConfirm = false },
            title = { Text(stringResource(R.string.new_game_confirm_title)) },
            text = { Text(stringResource(R.string.new_game_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startNewGame()
                        showNewGameConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.restart_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameConfirm = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}

/**
 * The board plus its win/game-over overlays, stacked in a [Box].
 *
 * This lives in its own composable (rather than inline in [GameScreen]'s Column) on purpose: it
 * removes the surrounding ColumnScope so the [AnimatedVisibility] calls below unambiguously resolve
 * to the plain Box-friendly overload instead of `ColumnScope.AnimatedVisibility`.
 */
@Composable
private fun BoardWithOverlays(
    uiState: GameUiState,
    animate: Boolean,
    onRestart: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        GameBoard(
            tiles = uiState.tiles,
            rows = uiState.rows,
            cols = uiState.cols,
            modifier = Modifier.fillMaxWidth(),
            animate = animate,
            mergingTiles = uiState.mergingTiles
        )

        AnimatedVisibility(
            visible = uiState.isGameOver,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GameOverlay(
                title = stringResource(R.string.game_over_title),
                message = stringResource(R.string.game_over_message),
                buttonText = stringResource(R.string.restart_button),
                onButtonClick = onRestart
            )
        }

        AnimatedVisibility(
            visible = uiState.hasWon && !uiState.continuePlayingAfterWin && !uiState.isGameOver,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GameOverlay(
                title = stringResource(R.string.win_title),
                message = stringResource(R.string.win_message, uiState.winTarget),
                buttonText = stringResource(R.string.keep_going_button),
                onButtonClick = onContinue
            )
        }
    }
}

/** Semi-transparent overlay shown on top of the board for both the win and game-over states. */
@Composable
private fun GameOverlay(
    title: String,
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onButtonClick) { Text(buttonText) }
        }
    }
}
