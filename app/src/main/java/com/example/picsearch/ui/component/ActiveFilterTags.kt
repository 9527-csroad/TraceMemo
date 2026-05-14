package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filter.timeRange?.let { range ->
            FilterTag(label = formatTimeRange(range), onClear = onClearTime)
        }
        selectedCluster?.let { cluster ->
            FilterTag(label = cluster.displayName.take(8), onClear = onClearLocation)
        }
        filter.sceneTags.forEach { tag ->
            FilterTag(label = tag, onClear = { onClearScene(tag) })
        }
        Text(
            text = "+ 筛选",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onOpenFilterPanel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FilterTag(label: String, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = " ✕",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.clickable(onClick = onClear),
        )
    }
}

fun formatTimeRange(r: TimeRange): String {
    val fmt = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
    val start = fmt.format(Date(r.startMillis))
    val endMonth = fmt.format(Date(r.endMillis))
    return if (start == endMonth) start else "$start ~ $endMonth"
}
