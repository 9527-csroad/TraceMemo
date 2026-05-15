package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveFilterTags(
    filter: SearchFilter,
    selectedCluster: LocationCluster?,
    onClearTime: () -> Unit,
    onClearLocation: () -> Unit,
    onClearScene: (String) -> Unit,
    onOpenFilterPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filter.timeRange?.let { range ->
            FilterChip(label = formatTimeRange(range), onClear = onClearTime)
        }
        selectedCluster?.let { cluster ->
            FilterChip(label = cluster.displayName.take(8), onClear = onClearLocation)
        }
        filter.sceneTags.forEach { tag ->
            FilterChip(label = tag, onClear = { onClearScene(tag) })
        }
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .clickable(onClick = onOpenFilterPanel),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = "筛选",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, onClear: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(100)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "  ✕",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
    }
}

fun formatTimeRange(r: TimeRange): String {
    val fmt = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
    val start = fmt.format(Date(r.startMillis))
    val endMonth = fmt.format(Date(r.endMillis))
    return if (start == endMonth) start else "$start ~ $endMonth"
}
