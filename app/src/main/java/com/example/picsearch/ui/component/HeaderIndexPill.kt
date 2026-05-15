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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picsearch.ui.theme.Tertiary

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
                .background(
                    when (pillState) {
                        is IndexPillState.Idle -> MaterialTheme.colorScheme.primary
                        is IndexPillState.Indexing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        is IndexPillState.Done -> Tertiary.copy(alpha = 0.15f)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (pillState) {
                is IndexPillState.Idle -> {
                    Text(
                        text = "索引",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = " ▸",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }

                is IndexPillState.Indexing -> {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Tertiary, CircleShape),
                    )
                    Text(
                        text = " ${pillState.indexed} / ${pillState.total}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = " ▸",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }

                is IndexPillState.Done -> {
                    Text(
                        text = "已完成",
                        color = Tertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
