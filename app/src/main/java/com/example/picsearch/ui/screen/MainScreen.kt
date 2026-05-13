package com.example.picsearch.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.picsearch.MainViewModel
import com.example.picsearch.data.LocationBounds
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.TimeRange
import com.example.picsearch.ui.component.ActiveFilterTags
import com.example.picsearch.ui.component.EmptyStateView
import com.example.picsearch.ui.component.FilterEntryRow
import com.example.picsearch.ui.component.ImageDetail
import com.example.picsearch.ui.component.ImageDetailSheet
import com.example.picsearch.ui.component.ImageGrid
import com.example.picsearch.ui.component.IndexProgressView
import com.example.picsearch.ui.component.NoResultsView
import com.example.picsearch.ui.component.SearchFilterPanel
import com.example.picsearch.ui.component.SkeletonCard
import com.example.picsearch.ui.theme.Primary

@Composable
fun MainScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val count by vm.indexedCount.collectAsState()
    val results by vm.results.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val clusters by vm.clusters.collectAsState()
    val sceneLabels by vm.sceneLabels.collectAsState()
    val unlocatedCount by vm.unlocatedCount.collectAsState()
    val ctx = LocalContext.current

    var hasSearched by remember { mutableStateOf(false) }

    var showFilterPanel by remember { mutableStateOf(false) }
    var timeRange by remember { mutableStateOf<TimeRange?>(null) }
    var selectedCluster by remember { mutableStateOf<LocationCluster?>(null) }
    var selectedScenes by remember { mutableStateOf<List<String>>(emptyList()) }

    val onSceneToggle: (String) -> Unit = { label ->
        selectedScenes = if (label in selectedScenes)
            selectedScenes - label
        else
            selectedScenes + label
    }

    val filter by remember(timeRange, selectedCluster, selectedScenes) {
        derivedStateOf {
            SearchFilter(
                timeRange = timeRange,
                locationBounds = selectedCluster?.let {
                    LocationBounds.fromBucket(it.latBucket, it.lonBucket)
                },
                sceneTags = selectedScenes,
            )
        }
    }

    var selectedImage by remember { mutableStateOf<ImageDetail?>(null) }

    // Android 10+ 需要照片 + 位置权限才能读取 EXIF GPS
    val needLocationPerm = Build.VERSION.SDK_INT >= 29
    val basePermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { result ->
            val allGranted = result.values.all { it }
            if (allGranted) vm.startIndex()
        },
    )

    fun startIndexWithPermission() {
        val permissions = if (needLocationPerm) {
            arrayOf(basePermission, Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            arrayOf(basePermission)
        }
        permLauncher.launch(permissions)
    }

    val quickWorkInfos by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkLiveData("quick_index")
        .observeAsState()
    val fullWorkInfos by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkLiveData("full_index")
        .observeAsState()
    val workRunning = (quickWorkInfos?.firstOrNull()?.let {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    } ?: false) || (fullWorkInfos?.firstOrNull()?.let {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    } ?: false)

    // Empty state
    if (count == 0 && !workRunning) {
        EmptyStateView(
            title = "还没有索引照片",
            description = "开始索引你的照片，然后用文字描述就能找到它们。所有处理都在本机完成，隐私安全。",
            actionText = "📸 开始索引",
            onAction = { startIndexWithPermission() },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = "PicSearch",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "用文字找到你的照片",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Search bar
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("搜索 \"日落时的海滩\"...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) {
                        vm.search(query.trim(), filter, topK = 30)
                        hasSearched = true
                    }
                },
            ),
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        vm.search(query.trim(), filter, topK = 30)
                        hasSearched = true
                    }) {
                        Text("🔍", fontSize = 18.sp)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
        )

        // Active filter tags
        if (!filter.isEmpty || selectedCluster != null) {
            ActiveFilterTags(
                filter = filter,
                selectedCluster = selectedCluster,
                onClearTime = { timeRange = null },
                onClearLocation = { selectedCluster = null },
                onOpenFilterPanel = { showFilterPanel = !showFilterPanel },
            )
        }

        // Filter entry row
        FilterEntryRow(
            onTimeClick = { showFilterPanel = !showFilterPanel },
            onLocationClick = { showFilterPanel = !showFilterPanel },
            onSceneClick = { showFilterPanel = !showFilterPanel },
        )

        // Expandable filter panel
        AnimatedVisibility(visible = showFilterPanel) {
            SearchFilterPanel(
                timeRange = timeRange,
                onTimeRangeChange = { timeRange = it },
                selectedCluster = selectedCluster,
                onClusterChange = { selectedCluster = it },
                clusters = clusters,
                unlocatedCount = unlocatedCount,
                sceneLabels = sceneLabels,
                selectedScenes = selectedScenes,
                onSceneToggle = onSceneToggle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Search results
        if (hasSearched && results.isEmpty() && !isSearching) {
            NoResultsView(query = query)
        } else if (results.isNotEmpty()) {
            Text(
                text = "找到 ${results.size} 张照片",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ImageGrid(
                uris = results,
                onImageClick = { uri ->
                    val item = vm.getImageDetail(uri)
                    selectedImage = item
                },
                modifier = Modifier.padding(bottom = 80.dp),
            )
        } else if (isSearching) {
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                repeat(4) {
                    SkeletonCard(modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                }
            }
        }

        // Bottom index button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = Primary,
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clickable { startIndexWithPermission() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📸 索引照片",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "已索引 $count 张照片",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    // Index progress overlay
    if (workRunning && count < 100) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center,
        ) {
            IndexProgressView(
                indexedCount = count,
                totalCount = null,
                isQuickPhase = count < 100,
            )
        }
    }

    // Image detail sheet
    selectedImage?.let { detail ->
        ImageDetailSheet(
            detail = detail,
            onDismiss = { selectedImage = null },
        )
    }
}
