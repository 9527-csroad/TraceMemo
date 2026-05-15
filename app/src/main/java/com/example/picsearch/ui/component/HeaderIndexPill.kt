package com.example.picsearch.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picsearch.ui.theme.AccentGreen
import com.example.picsearch.ui.theme.Primary

sealed class IndexPillState {
    data object Idle : IndexPillState()
    data class Indexing(val indexed: Int, val total: Int) : IndexPillState()
    data object Done : IndexPillState()
}

@Composable
fun HeaderIndexPill(
    state: IndexPillState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "indexPillState",
    ) { pillState ->
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .then(
                    when (pillState) {
                        is IndexPillState.Idle -> Modifier.background(Primary)
                        is IndexPillState.Indexing -> Modifier.background(Color(0xFF1a1a1a))
                        is IndexPillState.Done -> Modifier.background(Color(0xFF1a3320))
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (pillState) {
                is IndexPillState.Idle -> {
                    Text(
                        text = "开始索引",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = " ▸",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                    )
                }

                is IndexPillState.Indexing -> {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AccentGreen, CircleShape),
                    )
                    Text(
                        text = " ${pillState.indexed} / ${pillState.total}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = " ▸",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                    )
                }

                is IndexPillState.Done -> {
                    Text(
                        text = "✓ 已完成",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
