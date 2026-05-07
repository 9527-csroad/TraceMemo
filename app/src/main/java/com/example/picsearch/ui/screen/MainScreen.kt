package com.example.picsearch.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.picsearch.MainViewModel
import com.example.picsearch.data.LocationBounds
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.TimeRange
import com.example.picsearch.ui.component.FilterToggleHeader
import com.example.picsearch.ui.component.ImageGrid
import com.example.picsearch.ui.component.SearchFilterPanel

@Composable
fun MainScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val ready by vm.ready.collectAsState()
    val count by vm.indexedCount.collectAsState()
    val results by vm.results.collectAsState()
    val clusters by vm.clusters.collectAsState()
    val unlocatedCount by vm.unlocatedCount.collectAsState()
    val ctx = LocalContext.current
    val workInfos by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkLiveData("index")
        .observeAsState()
    val workState = workInfos?.firstOrNull()?.state ?: WorkInfo.State.CANCELLED

    var filterExpanded by remember { mutableStateOf(false) }
    var timeRange by remember { mutableStateOf<TimeRange?>(null) }
    var selectedCluster by remember { mutableStateOf<LocationCluster?>(null) }
    val filter by remember(timeRange, selectedCluster) {
        derivedStateOf {
            SearchFilter(
                timeRange = timeRange,
                locationBounds = selectedCluster?.let {
                    LocationBounds.fromBucket(it.latBucket, it.lonBucket)
                },
            )
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) vm.startIndex()
        },
    )

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(text = if (ready) "模型已加载" else "模型未就绪")
        Spacer(Modifier.height(8.dp))
        Text(text = "已索引：$count")
        Spacer(Modifier.height(4.dp))
        Text(text = "索引状态：${workState.name}")
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            Button(onClick = { permLauncher.launch(permission) }) {
                Text("索引")
            }
            Spacer(Modifier.padding(6.dp))
            Button(onClick = { vm.refreshCount() }) {
                Text("刷新")
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("输入中文") },
        )

        Spacer(Modifier.height(4.dp))

        FilterToggleHeader(
            expanded = filterExpanded,
            selectedCount = filter.selectedCount,
            onToggle = { filterExpanded = !filterExpanded },
        )

        AnimatedVisibility(visible = filterExpanded) {
            SearchFilterPanel(
                timeRange = timeRange,
                onTimeRangeChange = { timeRange = it },
                selectedCluster = selectedCluster,
                onClusterChange = { selectedCluster = it },
                clusters = clusters,
                unlocatedCount = unlocatedCount,
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { vm.search(query, filter, topK = 10) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("搜索")
        }

        Spacer(Modifier.height(8.dp))

        ImageGrid(uris = results, modifier = Modifier.fillMaxSize())
    }
}
