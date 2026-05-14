package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ImageAspectRatio
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.picsearch.MainViewModel.ImageScore
import com.example.picsearch.util.ReverseGeocoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageDetail(
    val uri: String,
    val displayName: String?,
    val width: Int,
    val height: Int,
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val sceneTags: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailSheet(
    results: List<ImageScore>,
    initialUri: String,
    detail: ImageDetail,
    onDismiss: () -> Unit,
    onDetailChange: (String) -> ImageDetail,
    modifier: Modifier = Modifier,
) {
    val initialIndex = results.indexOfFirst { it.uri == initialUri }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { results.size }

    var currentDetail by remember { mutableStateOf(detail) }
    var currentUri by remember { mutableStateOf(initialUri) }

    LaunchedEffect(pagerState.currentPage) {
        val newUri = results[pagerState.currentPage].uri
        if (newUri != currentUri) {
            currentUri = newUri
            currentDetail = onDetailChange(newUri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${pagerState.currentPage + 1} / ${results.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier,
        ) { pageIndex ->
            val item = results[pageIndex]
            val showFullDetail = (item.uri == currentUri)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .size(1024)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFFE8E4DF),
                )

                if (item.sceneTags.isNotEmpty()) {
                    SceneTagsFlowRow(tags = item.sceneTags)
                }

                if (showFullDetail) {
                    Text(
                        text = "照片信息",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    MetadataList(detail = currentDetail)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneTagsFlowRow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MetadataList(detail: ImageDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Default.AccessTime,
            label = "拍摄时间",
            value = detail.dateTaken?.let { formatTimestamp(it) } ?: "未知",
        )
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Default.Place,
            label = "拍摄地点",
            value = formatLocation(detail.latitude, detail.longitude),
        )
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Default.ImageAspectRatio,
            label = "尺寸",
            value = "${detail.width} × ${detail.height}",
        )
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Default.InsertDriveFile,
            label = "文件名",
            value = detail.displayName ?: "未知",
        )
    }
}

@Composable
private fun MetadataItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = value,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun formatLocation(lat: Double?, lon: Double?): String {
    if (lat == null || lon == null) return "未知位置"
    val address = ReverseGeocoder.lookup(lat, lon)
    return address ?: "未知位置"
}
