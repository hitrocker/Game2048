package com.hitrocker.game2048.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.hitrocker.game2048.R
import com.hitrocker.game2048.data.LeaderboardEntry
import com.hitrocker.game2048.game.GridSize
import com.hitrocker.game2048.ui.theme.BoardBackground
import com.hitrocker.game2048.ui.theme.PlayAccent
import com.hitrocker.game2048.ui.theme.ScoreBoxBackground
import com.hitrocker.game2048.ui.theme.TileTextDark
import com.hitrocker.game2048.viewmodel.LeaderboardViewModel

/**
 * Global per-size leaderboard. Lets the player switch between board sizes, see the top scores,
 * and optionally sign in (email/Google) so their name and identity stick across devices. Signed-out
 * players still appear as anonymous guests once they set a name.
 */
@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val storedName by viewModel.playerName.collectAsState()
    val currentUid = viewModel.currentUid()

    var showNameDialog by remember { mutableStateOf(false) }

    // Refresh whenever the screen is entered so a best just set in-game (or a fresh sign-in) appears.
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        // Top bar: back button + title.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_label),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.leaderboard_label),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Top players · ${uiState.size.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Size selector: every board size as a chip, the active one highlighted.
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(GridSize.entries.toList(), key = { it.label }) { size ->
                SizeChip(
                    label = size.label,
                    selected = size == uiState.size,
                    onClick = { viewModel.selectSize(size) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Account row: who you're playing as, plus sign-in/out and rename controls.
        AccountRow(
            displayName = authState.displayName?.takeIf { it.isNotBlank() }
                ?: storedName.takeIf { it.isNotBlank() }
                ?: "Guest",
            signedIn = authState.signedIn,
            onEditName = { showNameDialog = true },
            onSignIn = onSignIn,
            onSignOut = { viewModel.signOut() }
        )

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PlayAccent
                    )
                }

                uiState.error -> {
                    CenteredMessage(
                        text = stringResource(R.string.leaderboard_error),
                        actionLabel = stringResource(R.string.retry_button),
                        onAction = { viewModel.load() }
                    )
                }

                uiState.entries.isEmpty() -> {
                    CenteredMessage(text = stringResource(R.string.leaderboard_empty))
                }

                else -> {
                    LeaderboardContent(
                        entries = uiState.entries,
                        currentUid = currentUid
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        NameDialog(
            initial = storedName,
            onConfirm = { name ->
                viewModel.setPlayerName(name)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }
}

@Composable
private fun SizeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PlayAccent else BoardBackground.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AccountRow(
    displayName: String,
    signedIn: Boolean,
    onEditName: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScoreBoxBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Playing as",
                fontSize = 12.sp,
                color = TileTextDark.copy(alpha = 0.7f)
            )
            Text(
                text = displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TileTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Guests can rename themselves; signed-in users carry their provider name.
        if (!signedIn) {
            IconButton(onClick = onEditName) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit_name_label),
                    tint = TileTextDark
                )
            }
        }
        TextButton(onClick = if (signedIn) onSignOut else onSignIn) {
            Text(
                text = stringResource(
                    if (signedIn) R.string.sign_out_button else R.string.sign_in_button
                ),
                color = PlayAccent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val GoldColor = Color(0xFFD4AF37)
private val SilverColor = Color(0xFFAEB4BC)
private val BronzeColor = Color(0xFFC77B3B)

private fun medalColor(rank: Int): Color = when (rank) {
    1 -> GoldColor
    2 -> SilverColor
    3 -> BronzeColor
    else -> BoardBackground
}

private fun formatScore(score: Int): String = String.format(Locale.US, "%,d", score)

/**
 * The populated leaderboard: a podium for the top 3, a scrollable list for ranks 4+, and the
 * current player's row pinned to the bottom whenever it has scrolled out of view.
 */
@Composable
private fun LeaderboardContent(entries: List<LeaderboardEntry>, currentUid: String?) {
    val top3 = entries.take(3)
    val listState = rememberLazyListState()

    val currentIndex = entries.indexOfFirst { it.uid == currentUid }
    val currentVisible by remember(entries, currentUid) {
        derivedStateOf {
            if (currentIndex < 0) true
            else listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (top3.isNotEmpty()) {
            Podium(top3 = top3, currentUid = currentUid)
            Spacer(Modifier.height(16.dp))
        }

        // Full ranked list (1..N) below the podium.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.uid }) { index, entry ->
                LeaderboardRow(
                    rank = index + 1,
                    entry = entry,
                    isCurrentPlayer = entry.uid == currentUid
                )
            }
        }

        // Pin the current player's row when it has scrolled out of view.
        if (currentIndex >= 0 && !currentVisible) {
            Spacer(Modifier.height(6.dp))
            LeaderboardRow(
                rank = currentIndex + 1,
                entry = entries[currentIndex],
                isCurrentPlayer = true
            )
        }
    }
}

/** Top-3 podium: 1st place raised in the centre, 2nd to the left, 3rd to the right. */
@Composable
private fun Podium(top3: List<LeaderboardEntry>, currentUid: String?) {
    val first = top3.getOrNull(0)
    val second = top3.getOrNull(1)
    val third = top3.getOrNull(2)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (second != null) {
            PodiumPlace(
                place = 2,
                entry = second,
                isCurrentPlayer = second.uid == currentUid,
                pedestalHeight = 64.dp,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (first != null) {
            PodiumPlace(
                place = 1,
                entry = first,
                isCurrentPlayer = first.uid == currentUid,
                pedestalHeight = 92.dp,
                modifier = Modifier.weight(1f)
            )
        }

        if (third != null) {
            PodiumPlace(
                place = 3,
                entry = third,
                isCurrentPlayer = third.uid == currentUid,
                pedestalHeight = 48.dp,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PodiumPlace(
    place: Int,
    entry: LeaderboardEntry,
    isCurrentPlayer: Boolean,
    pedestalHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val color = medalColor(place)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (place == 1) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = GoldColor,
                modifier = Modifier
                    .size(26.dp)
                    .padding(bottom = 2.dp)
            )
        }
        Text(
            text = entry.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCurrentPlayer) PlayAccent else TileTextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = formatScore(entry.score),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = TileTextDark
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(color.copy(alpha = if (isCurrentPlayer) 0.95f else 0.8f)),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "$place",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isCurrentPlayer: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrentPlayer) PlayAccent.copy(alpha = 0.16f) else ScoreBoxBackground
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrentPlayer) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PlayAccent)
            )
            Spacer(Modifier.width(10.dp))
        }
        RankBadge(rank)
        Text(
            text = entry.name,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            fontSize = 16.sp,
            fontWeight = if (isCurrentPlayer) FontWeight.Black else FontWeight.SemiBold,
            color = TileTextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isCurrentPlayer) {
            YouPill()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = formatScore(entry.score),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = TileTextDark
        )
    }
}

/** Circular rank badge, used for ranks 4+ in the list. */
@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(BoardBackground.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$rank",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = TileTextDark.copy(alpha = 0.85f)
        )
    }
}

/** Small accent "You" tag shown on the current player's row. */
@Composable
private fun YouPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PlayAccent)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "You",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
            modifier = Modifier
                .size(56.dp)
                .padding(bottom = 12.dp)
        )
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = PlayAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_name_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 20) text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.player_name_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}
