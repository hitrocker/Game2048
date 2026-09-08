package com.hitrocker.game2048.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitrocker.game2048.R
import com.hitrocker.game2048.game.GridSize
import com.hitrocker.game2048.ui.components.HowToPlayDialog
import com.hitrocker.game2048.ui.components.SettingsSheet
import com.hitrocker.game2048.ui.components.tileBackgroundColor
import com.hitrocker.game2048.ui.theme.BoardBackground
import com.hitrocker.game2048.ui.theme.EmptyCell
import com.hitrocker.game2048.ui.theme.PlayAccent
import com.hitrocker.game2048.ui.theme.TileTextDark
import com.hitrocker.game2048.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Landing screen: a board-size picker shown as a two-page pager (squares, then rectangles). Each
 * page is a 2x2 grid of preview cards; tapping one selects that size, and the large Play button
 * starts a game of the selected size. A gear icon in the top corner opens Settings.
 *
 * @param onPlay Invoked with the chosen [GridSize] when the player taps Play.
 * @param viewModel Supplies the selected size, per-size best score, and the sound/haptics prefs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onPlay: (GridSize) -> Unit,
    onOpenLeaderboard: () -> Unit,
    viewModel: HomeViewModel,
    accountName: String,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSize by viewModel.selectedSize.collectAsState()
    val bestScore by viewModel.bestScore.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val showMoveCounter by viewModel.showMoveCounter.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val hasSeenHowToPlay by viewModel.hasSeenHowToPlay.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showHowToPlay by remember { mutableStateOf(false) }

    // First-launch onboarding: auto-open the guide once for brand-new players (flag definitively
    // false, not just still loading), then immediately mark it seen so it never interrupts again.
    LaunchedEffect(hasSeenHowToPlay) {
        if (hasSeenHowToPlay == false) {
            showHowToPlay = true
            viewModel.markHowToPlaySeen()
        }
    }

    val pages = remember { listOf(GridSize.squares, GridSize.rectangles) }
    val initialPage = if (selectedSize in GridSize.rectangles) 1 else 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    // Keep the pager on the page that holds the selected size (e.g. open on the rectangles page when
    // a rectangle is the persisted choice, which the initial value may miss before DataStore emits).
    LaunchedEffect(selectedSize) {
        val target = if (selectedSize in GridSize.rectangles) 1 else 0
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Always-visible help affordance so players can re-open the rules any time.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BoardBackground.copy(alpha = 0.5f))
                        .clickable { showHowToPlay = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.how_to_play_label),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                SizeGridPage(
                    sizes = pages[page],
                    selectedSize = selectedSize,
                    onSelect = viewModel::setSelectedSize
                )
            }

            Spacer(Modifier.height(12.dp))

            // Page tabs: a square glyph and a rectangle glyph, the active page highlighted.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PageTab(
                    cols = 3,
                    rows = 3,
                    active = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                )
                PageTab(
                    cols = 3,
                    rows = 4,
                    active = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Best ${selectedSize.label}: $bestScore",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )

            // Bottom action row: settings (left), the wide Play button (centre), leaderboard (right).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BoardBackground.copy(alpha = 0.5f))
                        .clickable { showSettings = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_label),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PlayAccent)
                        .clickable { onPlay(selectedSize) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Play",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BoardBackground.copy(alpha = 0.5f))
                        .clickable { onOpenLeaderboard() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = stringResource(R.string.leaderboard_label),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
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

    if (showHowToPlay) {
        HowToPlayDialog(onDismiss = { showHowToPlay = false })
    }
}

/** One pager page: a 2x2 grid of board-size preview cards. */
@Composable
private fun SizeGridPage(
    sizes: List<GridSize>,
    selectedSize: GridSize,
    onSelect: (GridSize) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sizes.chunked(2).forEach { rowSizes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowSizes.forEach { size ->
                    SizeCard(
                        size = size,
                        selected = size == selectedSize,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** A single selectable board-size preview: a mini rendered board with the size label below it. */
@Composable
private fun SizeCard(
    size: GridSize,
    selected: Boolean,
    onSelect: (GridSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val sample = remember(size) { sampleTiles(size) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect(size) }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val aspect = size.aspectRatio
            val boardWidth = if (maxWidth / aspect <= maxHeight) maxWidth else maxHeight * aspect
            MiniBoard(
                size = size,
                sample = sample,
                faded = !selected,
                modifier = Modifier.width(boardWidth)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = size.label,
            fontSize = 20.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) {
                TileTextDark
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            }
        )
    }
}

/**
 * A static, non-interactive miniature of a board: the tan frame with empty wells and a scattering
 * of [sample] tiles. Unselected previews are dimmed via [faded].
 */
@Composable
private fun MiniBoard(
    size: GridSize,
    sample: Map<Pair<Int, Int>, Int>,
    faded: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha = if (faded) 0.45f else 1f
    Column(
        modifier = modifier
            .aspectRatio(size.aspectRatio)
            .clip(RoundedCornerShape(6.dp))
            .background(BoardBackground.copy(alpha = alpha))
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (row in 0 until size.rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (col in 0 until size.cols) {
                    val value = sample[row to col]
                    val cellColor =
                        if (value == null) EmptyCell else tileBackgroundColor(value)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cellColor.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

/** A small grid glyph used as a page-indicator tab; highlighted when [active]. */
@Composable
private fun PageTab(
    cols: Int,
    rows: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) BoardBackground.copy(alpha = 0.5f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        val glyphAspect = cols.toFloat() / rows.toFloat()
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(glyphAspect),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(cols) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    EmptyCell.copy(alpha = if (active) 1f else 0.6f)
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deterministic sample tiles for a size's preview, so each card always looks the same but the eight
 * sizes look distinct. Roughly a third of cells are filled with small classic values.
 */
private fun sampleTiles(size: GridSize): Map<Pair<Int, Int>, Int> {
    val random = Random(size.ordinal * 31 + 7)
    val values = listOf(2, 4, 8, 16, 32, 64)
    val allCells = buildList {
        for (row in 0 until size.rows) {
            for (col in 0 until size.cols) {
                add(row to col)
            }
        }
    }
    val count = (size.rows * size.cols / 3).coerceAtLeast(3)
    return allCells.shuffled(random).take(count).associateWith { values.random(random) }
}
