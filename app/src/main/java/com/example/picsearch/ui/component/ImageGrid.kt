package com.example.picsearch.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
    ) {
        itemsIndexed(uris, key = { _, item -> item.uri }) { index, item ->
            AnimatedGridItem(index = index) {
                ImageCard(item = item, onClick = onImageClick)
            }
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

@Composable
private fun ImageCard(item: ImageScore, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .padding(6.dp)
            .clip(MaterialTheme.shapes.medium)
            .aspectRatio(1f)
            .clickable { onClick(item.uri) },
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
        // 场景标签（左上角）
        item.sceneTags.takeIf { it.isNotEmpty() }?.let { tags ->
            val primary = tags.first()
            Text(
                text = primary,
                fontSize = 10.sp,
                color = Color(0xFF3F3F46),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xE6FFFFFF), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        // 相似度分数（右下角）
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
