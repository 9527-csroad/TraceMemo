package com.example.picsearch.ui.component

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.picsearch.MainViewModel.ImageScore

@Composable
fun ImageGrid(
    uris: List<ImageScore>,
    onImageClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (uris.isEmpty()) return

    var longClickedUri by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    // 使用非 lazy 的 Column 布局，避免与外层 verticalScroll 冲突
    // 搜索结果最多 30 张，不需要 lazy
    Column(modifier = modifier) {
        val columns = 2
        val rowCount = (uris.size + columns - 1) / columns
        for (row in 0 until rowCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < uris.size) {
                        val item = uris[index]
                        AnimatedGridItem(index = index) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .combinedClickable(
                                        onClick = { onImageClick(item.uri) },
                                        onLongClick = { longClickedUri = item.uri },
                                    ),
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.uri)
                                        .size(512)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                )
                                item.sceneTags.takeIf { it.isNotEmpty() }?.let { tags ->
                                    Text(
                                        text = tags.first(),
                                        fontSize = 10.sp,
                                        color = Color(0xFF3F3F46),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(Color(0xE6FFFFFF), MaterialTheme.shapes.small)
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                                Text(
                                    text = "${(item.score * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color(0xCC000000), MaterialTheme.shapes.small)
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                        if (col < columns - 1) {
                            Spacer(modifier = Modifier.width(0.dp))
                        }
                    }
                }
            }
        }
    }

    // Long press dropdown menu
    longClickedUri?.let { uri ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { longClickedUri = null },
        ) {
            DropdownMenuItem(
                text = { Text("查看详情") },
                onClick = {
                    longClickedUri = null
                    onImageClick(uri)
                },
            )
            DropdownMenuItem(
                text = { Text("分享") },
                onClick = {
                    longClickedUri = null
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(intent, null))
                },
            )
        }
    }
}

@Composable
private fun AnimatedGridItem(index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = index * 50)) +
                slideInVertically(
                    initialOffsetY = { it / 5 },
                    animationSpec = tween(durationMillis = 300, delayMillis = index * 50),
                ),
    ) {
        content()
    }
}
