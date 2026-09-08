package com.hitrocker.game2048.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitrocker.game2048.ui.theme.Brown40
import com.hitrocker.game2048.ui.theme.ScoreBoxBackground
import com.hitrocker.game2048.ui.theme.TileTextDark

/**
 * A wide rounded card showing a [label] (e.g. "SCORE") on the left and its numeric [value] on the
 * right. Used for both the current score and the best score so the two stay visually consistent;
 * the caller typically gives each `Modifier.weight(1f)` so they split the row evenly.
 *
 * The displayed number animates when it changes, giving score increases a small "count up" feel
 * instead of jumping instantly.
 */
@Composable
fun ScoreCard(label: String, value: Int, modifier: Modifier = Modifier) {
    val animatedValue by animateIntAsState(targetValue = value, animationSpec = tween(200), label = "scoreValue")

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ScoreBoxBackground)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Brown40,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = animatedValue.toString(),
            color = TileTextDark,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
