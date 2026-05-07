package com.example.picsearch.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.TimeRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class TimePreset(val label: String) {
    WEEK("近一周"), MONTH("近一月"), QUARTER("近三月"),
    THIS_YEAR("今年"), LAST_YEAR("去年"), CUSTOM("自定义…");

    fun range(now: Long = System.currentTimeMillis()): TimeRange? = when (this) {
        WEEK -> TimeRange(now - 7L * 24 * 3600 * 1000, now)
        MONTH -> TimeRange(now - 30L * 24 * 3600 * 1000, now)
        QUARTER -> TimeRange(now - 90L * 24 * 3600 * 1000, now)
        THIS_YEAR -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            TimeRange(cal.timeInMillis, now)
        }
        LAST_YEAR -> {
            val cal = Calendar.getInstance().apply {
                add(Calendar.YEAR, -1)
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 31)
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            TimeRange(start, cal.timeInMillis)
        }
        CUSTOM -> null
    }
}

private fun formatTimeRange(r: TimeRange): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return "${fmt.format(Date(r.startMillis))} ~ ${fmt.format(Date(r.endMillis))}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterPanel(
    timeRange: TimeRange?,
    onTimeRangeChange: (TimeRange?) -> Unit,
    selectedCluster: LocationCluster?,
    onClusterChange: (LocationCluster?) -> Unit,
    clusters: List<LocationCluster>,
    unlocatedCount: Int,
    modifier: Modifier = Modifier,
) {
    var activePreset by remember { mutableStateOf<TimePreset?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMoreClusters by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("时间", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.padding(top = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = activePreset == preset,
                        onClick = {
                            if (preset == TimePreset.CUSTOM) {
                                showDatePicker = true
                            } else {
                                val r = preset.range()
                                if (r != null && activePreset == preset) {
                                    activePreset = null
                                    onTimeRangeChange(null)
                                } else {
                                    activePreset = preset
                                    onTimeRangeChange(r)
                                }
                            }
                        },
                        label = { Text(preset.label) },
                    )
                }
                if (timeRange != null) {
                    TextButton(onClick = {
                        activePreset = null
                        onTimeRangeChange(null)
                    }) {
                        Text("×")
                    }
                }
            }
            if (timeRange != null) {
                Text(
                    text = formatTimeRange(timeRange),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }

            Spacer(Modifier.padding(top = 10.dp))
            val locatedTotal = clusters.sumOf { it.count }
            Text(
                "地点（$locatedTotal 张含位置${if (unlocatedCount > 0) "，$unlocatedCount 张无位置将被排除" else ""}）",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.padding(top = 4.dp))
            if (clusters.isEmpty()) {
                Text(
                    text = if (unlocatedCount > 0) "你的照片都没有 GPS 信息，无法按地点筛选"
                    else "还没有索引任何图片",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val visible = clusters.take(5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visible.forEach { c ->
                        FilterChip(
                            selected = selectedCluster == c,
                            onClick = {
                                onClusterChange(if (selectedCluster == c) null else c)
                            },
                            label = { Text(c.displayName) },
                        )
                    }
                    if (clusters.size > visible.size) {
                        TextButton(onClick = { showMoreClusters = true }) {
                            Text("更多(${clusters.size - visible.size})")
                        }
                    }
                    if (selectedCluster != null) {
                        TextButton(onClick = { onClusterChange(null) }) {
                            Text("×")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = timeRange?.startMillis,
            initialSelectedEndDateMillis = timeRange?.endMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = state.selectedStartDateMillis
                        val e = state.selectedEndDateMillis
                        if (s != null && e != null) {
                            activePreset = TimePreset.CUSTOM
                            onTimeRangeChange(TimeRange(s, e + 24L * 3600 * 1000 - 1))
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DateRangePicker(state = state, modifier = Modifier.heightIn(max = 560.dp))
        }
    }

    if (showMoreClusters) {
        AlertDialog(
            onDismissRequest = { showMoreClusters = false },
            confirmButton = {
                TextButton(onClick = { showMoreClusters = false }) { Text("关闭") }
            },
            title = { Text("全部地点聚类") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(clusters, key = { "${it.latBucket}_${it.lonBucket}" }) { c ->
                        TextButton(
                            onClick = {
                                onClusterChange(c)
                                showMoreClusters = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                c.displayName,
                                modifier = Modifier.fillMaxWidth(),
                                fontWeight = if (selectedCluster == c) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun FilterToggleHeader(
    expanded: Boolean,
    selectedCount: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onToggle, modifier = modifier) {
        Text(if (expanded) "▲" else "▼")
        Spacer(Modifier.width(4.dp))
        Text(if (selectedCount > 0) "筛选（已选 $selectedCount 项）" else "筛选")
    }
}
