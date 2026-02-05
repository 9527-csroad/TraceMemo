package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.picsearch.MainViewModel.ImageScore

@Composable
fun ImageGrid(uris: List<ImageScore>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = modifier,
    ) {
        items(uris, key = { it.uri }) { item ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .size(256)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Text(
                    text = String.format("%.3f", item.score),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0x66000000))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

