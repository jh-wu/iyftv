package com.iyftv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.Category
import com.iyftv.app.ui.common.Load
import com.iyftv.app.ui.common.Message
import com.iyftv.app.ui.common.PosterCard
import com.iyftv.app.ui.common.VideoRow

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenVideo: (String) -> Unit,
    onResume: (WatchRecord) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onCheckUpdate: () -> Unit,
    version: String,
) {
    val rows by vm.rows.collectAsState()
    val allRecent by vm.continueWatching.collectAsState()
    val recent = allRecent.filterNot { it.isFinished }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item(key = "header") {
            Row(
                Modifier.padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("iyfTV", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onSearch) { Text("搜索") }
                Button(onClick = onHistory) { Text("观看记录") }
                Button(onClick = onCheckUpdate) { Text("检查更新") }
                Text(
                    version,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "continue") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("继续观看", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(recent, key = { it.videoKey }) { r ->
                            PosterCard(r.title, r.imageUrl, r.episodeName, onClick = { onResume(r) }, progress = r.progress)
                        }
                    }
                }
            }
        }
        when (val state = rows) {
            is Load.Loading -> item(key = "loading") { Message("加载中…") }
            is Load.Failed -> item(key = "error") { Message("加载失败：${state.message}", onRetry = vm::load) }
            is Load.Ready -> items(state.value, key = { it.category.id }) { row ->
                VideoRow(
                    title = row.category.name,
                    videos = row.videos,
                    onOpen = { onOpenVideo(it.key) },
                    onMore = { onOpenCategory(row.category) },
                )
            }
        }
    }
}
