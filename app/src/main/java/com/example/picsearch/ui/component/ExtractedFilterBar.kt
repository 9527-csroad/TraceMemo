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
import com.example.picsearch.util.ExtractedFilter

@Composable
fun ExtractedFilterBar(
    extracted: ExtractedFilter,
    onClearTime: () -> Unit,
    onClearLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (extracted.timeRange == null && extracted.locationBounds == null) return

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        extracted.timeRange?.let {
            FilterTag(label = formatTimeRange(it), onClear = onClearTime)
        }
        extracted.locationBounds?.let {
            val name = extracted.locationName ?: "已选地点"
            FilterTag(label = name, onClear = onClearLocation)
        }
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
