# 2048 — Android (Jetpack Compose, MVVM)

A clean, production-quality MVP of the classic 2048 puzzle game, built with Kotlin and Jetpack
Compose using an MVVM architecture and StateFlow for state management.

## Features

- Classic 4×4 board, starts with two random tiles (2 or 4)
- Swipe Up / Down / Left / Right to move
- Correct slide + merge-once-per-move rules
- Score updates on every merge; best score persisted locally (DataStore)
- New tile spawns after every valid move
- Game Over detection and Win (2048 tile) detection, with a "Keep Going" option to play past 2048
- Restart button
- Smooth tile-slide, spawn-pop, and merge-pulse animations
- Material 3 theming with a palette matching the original 2048 game

## Project Structure

```
app/
├── src/main/java/com/example/game2048/
│   ├── MainActivity.kt              # Single Activity entry point; wires ViewModel + Theme
│   ├── game/                        # Pure game logic - no Android/Compose dependencies
│   │   ├── Tile.kt                  # Immutable tile model (id, value, row, col, anim flags)
│   │   ├── Board.kt                 # Immutable 4x4 grid wrapper
│   │   ├── MoveDirection.kt         # UP / DOWN / LEFT / RIGHT enum
│   │   └── GameEngine.kt            # Move/merge/score/game-over/win rules (the "brain")
│   ├── viewmodel/
│   │   └── GameViewModel.kt         # Exposes an immutable GameUiState via StateFlow
│   ├── data/
│   │   └── PreferencesManager.kt    # DataStore wrapper that persists the best score
│   └── ui/
│       ├── theme/                   # Color.kt, Type.kt, Theme.kt (Material 3)
│       ├── components/              # GameBoard, TileView, ScoreCard, TileColors
│       └── screens/
│           └── GameScreen.kt        # Top-level screen composing everything together
└── src/test/java/com/example/game2048/game/
    └── GameEngineTest.kt            # Unit tests for GameEngine (18 test cases)
```

## Architecture

**MVVM with a strict separation between game rules, state, and UI:**

- **`game/` (Model)** — `GameEngine` is a stateless, pure Kotlin object. Every function takes a
  `Board` in and returns a new `Board` (or a derived value) out. It has zero Android
  dependencies, which is what makes it trivial to unit test on the plain JVM with no emulator.
  All four swipe directions reduce to the same row/column "compress and merge" routine
  (`mergeLine`), so the merge rule is implemented exactly once and reused via the
  `extractLines` / `buildGridFromLines` transforms — eliminating duplicated logic across
  directions.

- **`viewmodel/` (ViewModel)** — `GameViewModel` holds the *real* `Board` privately and exposes
  only an immutable `StateFlow<GameUiState>`. It contains no game rules of its own: its only job
  is deciding *when* to call into `GameEngine` (on a swipe) and folding the result into UI
  state, plus persisting the best score when it improves. The UI can never mutate state
  directly — it only reads `uiState` and calls `onSwipe()` / `startNewGame()`.

- **`ui/` (View)** — Composables are small and single-purpose (`GameBoard`, `TileView`,
  `ScoreCard`, `GameOverlay`). `GameScreen` is intentionally "dumb": it renders `uiState` and
  forwards user gestures back to the ViewModel, with no business logic of its own.

- **`data/`** — `PreferencesManager` is the only class that knows persistence exists. It wraps
  Jetpack DataStore Preferences behind a `Flow<Int>` + `suspend fun save(...)`, so the
  ViewModel doesn't need to know *how* the best score is stored.

**Tile animation approach:** each `Tile` carries a stable `id` that survives across moves. The
board renders tiles with absolute, individually animated offsets (`animateDpAsState` on
`row`/`col`) rather than a grid layout, and wraps each one in `key(tile.id)` — this is what lets
Compose interpolate a tile's position smoothly between cells instead of treating every move as
"destroy everything, redraw from scratch". `isNew` and `isMerged` flags on `Tile` (cleared each
move) trigger the spawn-pop and merge-pulse animations respectively.

## How to Run

1. Open the project root folder in Android Studio (Koala or newer recommended).
2. Let Gradle sync — Android Studio will generate the Gradle wrapper jar/scripts automatically
   the first time you open or build the project (only `gradle-wrapper.properties` is checked
   in, pointing at Gradle 8.7).
3. Select a device or emulator running **API 26 (Android 8.0) or higher**.
4. Click **Run ▶**.

To run the unit tests from the command line once the wrapper is present:

```
./gradlew testDebugUnitTest
```

Or right-click `GameEngineTest.kt` in Android Studio and choose **Run**.

## Testing

`GameEngineTest.kt` covers, with 18 test cases:
- Movement (compression with no merge) in all four directions: left, right, up, down
- Merge rules: simple merge, a row of four equal tiles merging into exactly two pairs (never
  cascading into one tile), three-in-a-row merging only the leading pair, and tiles with
  different values never merging
- A no-op swipe (nothing changes) correctly reporting `moved = false`
- Score accumulation across multiple independent merges in a single move
- Game-over detection: full board with no merges available, full board that still has one
  merge available, and a board that still has empty cells
- Win detection: both when a 2048 tile exists and when it doesn't
- Random tile spawning: exactly one tile of value 2 or 4 added to an empty board, no-op on a
  full board, and `newGame()` starting with exactly two tiles

All 18 tests pass.

## Performance Notes

- `Tile`, `Board`, and `GameUiState` are annotated `@Immutable` so the Compose compiler can
  treat them as stable and skip unnecessary recomposition.
- Each tile carries a stable `id` used as a Compose `key`, so a move only recomposes/animates
  the tiles that actually changed instead of rebuilding the whole board.
- `GameUiState` is a single immutable data class collected once via `collectAsState()`, so the
  whole screen recomposes only when something the player can actually see has changed.

## Future Improvements

- **Undo move** — keep a small history of past `Board`/score states.
- **Sound & haptics** — subtle feedback on merge and on game over/win.
- **Full process-death state restoration** — currently only the best score survives process
  death; the in-progress board could be persisted too (e.g. serialized into DataStore or
  `SavedStateHandle`).
- **Tile exit animation** — the tile "absorbed" by a merge currently disappears instantly
  rather than animating out; a short fade/scale-out would make merges feel even smoother.
- **Larger board sizes** (5×5, 6×6) as a difficulty option — `Board.SIZE` is already
  centralized, though `GameEngine`/UI would need it to become configurable rather than a
  constant.
- **Dark theme polish & dynamic color toggle** in a settings screen.
- **Tablet / landscape layout** — the board currently assumes a portrait phone layout.
- **Accessibility** — TalkBack content descriptions for tiles and swipe-alternative controls
  (e.g. on-screen directional buttons) for players who can't perform swipe gestures.
- **Proper launcher icon & Play Store assets** — the included adaptive icon is a simple
  placeholder vector; replace `ic_launcher_background.xml` / `ic_launcher_foreground.xml` (or
  swap in PNG mipmaps) with real artwork before publishing.
- **Leaderboard / cloud sync** of best scores via a backend, if multi-device sync is desired.
