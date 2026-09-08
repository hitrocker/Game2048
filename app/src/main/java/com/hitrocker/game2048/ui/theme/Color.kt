package com.hitrocker.game2048.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Material 3 seed colors (used for buttons, score cards, app chrome) ----
val Orange40 = Color(0xFFB36A1E)
val Orange80 = Color(0xFFFFB870)
val Beige90 = Color(0xFFFAF8EF)
val Brown40 = Color(0xFF7A6A58)
val DarkBackground = Color(0xFF2B2620)
val DarkSurface = Color(0xFF332C24)

// ---- Board chrome (classic flat 2048: opaque tan frame and wells) ----
val BoardBackground = Color(0xFFBBADA0) // classic tan board frame
val EmptyCell = Color(0xFFCDC1B4)       // lighter tan empty-cell wells
val ScoreBoxBackground = Color(0xFFEEE4DA) // light cream score cards
val ClassicPillBackground = Color(0xFFBBADA0) // tan "CLASSIC" badge behind the title
val PlayAccent = Color(0xFFF66B4E) // bright orange play button on the home picker
val DangerRed = Color(0xFFD32F2F) // destructive actions (delete account, inline errors)

// ---- Classic 2048 tile palette, one color per value ----
val Tile2 = Color(0xFFEEE4DA)
val Tile4 = Color(0xFFEDE0C8)
val Tile8 = Color(0xFFF2B179)
val Tile16 = Color(0xFFF59563)
val Tile32 = Color(0xFFF67C5F)
val Tile64 = Color(0xFFF65E3B)
val Tile128 = Color(0xFFEDCF72)
val Tile256 = Color(0xFFEDCC61)
val Tile512 = Color(0xFFEDC850)
val Tile1024 = Color(0xFFEDC53F)
val Tile2048 = Color(0xFFEDC22E)
val TileSuper = Color(0xFF3C3A32) // Fallback for any value beyond 2048.

val TileTextDark = Color(0xFF776E65) // Used on the lightest tiles (2, 4) for contrast.
val TileTextLight = Color(0xFFF9F6F2) // Used on every darker, higher-value tile.
