package com.iyftv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.Category
import com.iyftv.app.ui.category.CategoryViewModel
import com.iyftv.app.ui.common.AppIcons
import com.iyftv.app.ui.common.PosterCard
import com.iyftv.app.ui.common.VideoGrid

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    categoryViewModel: @Composable (Category) -> CategoryViewModel,
    onOpenVideo: (String) -> Unit,
    onResume: (WatchRecord) -> Unit,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val categories by vm.categories.collectAsState()
    val allRecent by vm.continueWatching.collectAsState()
    val recent = allRecent.filterNot { it.isFinished }
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("iyfTV", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            HeaderButton(Icons.Default.Search, "搜索", onSearch)
            HeaderButton(AppIcons.History, "观看记录", onHistory)
            HeaderButton(AppIcons.Update, "检查更新", onCheckUpdate)
        }
        if (categories.isEmpty()) return@Column

        val current = selected.coerceIn(categories.indices)
        TabRow(selectedTabIndex = current, modifier = Modifier.padding(start = 40.dp, top = 12.dp)) {
            categories.forEachIndexed { i, c ->
                Tab(selected = i == current, onFocus = { selected = i }, onClick = { selected = i }) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        }

        val category = categories[current]
        key(category.id) {
            val gridVm = categoryViewModel(category)
            val state by gridVm.state.collectAsState()
            VideoGrid(
                state,
                onOpen = { onOpenVideo(it.key) },
                onLoadMore = gridVm::loadMore,
                header = if (recent.isEmpty()) null else {
                    {
                        item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                            ContinueWatching(recent, onResume)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

@Composable
private fun ContinueWatching(recent: List<WatchRecord>, onResume: (WatchRecord) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("继续观看", style = MaterialTheme.typography.titleLarge)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 48.dp),
        ) {
            items(recent, key = { it.videoKey }) { r ->
                PosterCard(r.title, r.imageUrl, r.episodeName, onClick = { onResume(r) }, progress = r.progress)
            }
        }
    }
}
