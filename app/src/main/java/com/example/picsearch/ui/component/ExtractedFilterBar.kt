package com.example.picsearch.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        extracted.timeRange?.let {
            NlpFilterChip(label = formatTimeRange(it), onClear = onClearTime)
        }
        extracted.locationBounds?.let {
            val name = extracted.locationName ?: "已选地点"
            NlpFilterChip(label = name, onClear = onClearLocation)
        }
    }
}

@Composable
private fun NlpFilterChip(label: String, onClear: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(100)),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "自动",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = " $label",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "  ✕",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
    }
}
