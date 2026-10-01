package com.iyftv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.iyftv.app.ui.category.FilterBar
import com.iyftv.app.ui.common.AppIcons
import com.iyftv.app.ui.common.CardWidth
import com.iyftv.app.ui.common.Message
import com.iyftv.app.ui.common.PosterCard
import com.iyftv.app.ui.common.VideoGrid
import com.iyftv.app.ui.common.formatTime

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
    val selected by vm.selectedTab.collectAsState()
    // Coming back from a title, focus returns to the tab that was open, so it stays selected.
    val tabFocus = remember { FocusRequester() }

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

        // The six categories, then 继续观看 as the last tab.
        val tabNames = categories.map { it.name } + CONTINUE_TAB
        val current = selected.coerceIn(tabNames.indices)
        TabRow(selectedTabIndex = current, modifier = Modifier.padding(start = 40.dp, top = 12.dp)) {
            tabNames.forEachIndexed { i, name ->
                Tab(
                    selected = i == current,
                    onFocus = { vm.selectTab(i) },
                    onClick = { vm.selectTab(i) },
                    modifier = if (i == current) Modifier.focusRequester(tabFocus) else Modifier,
                ) {
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        }

        LaunchedEffect(Unit) { runCatching { tabFocus.requestFocus() } }

        if (current == categories.size) {
            ContinueWatching(recent, onResume)
            return@Column
        }
        val category = categories[current]
        key(category.id) {
            val gridVm = categoryViewModel(category)
            val state by gridVm.state.collectAsState()
            FilterBar(gridVm)
            VideoGrid(
                state,
                onOpen = { onOpenVideo(it.key) },
                onLoadMore = gridVm::loadMore,
                emptyText = if (gridVm.filter.collectAsState().value.isEmpty()) "没有内容" else "没有符合筛选条件的内容",
            )
        }
    }
}

private const val CONTINUE_TAB = "继续观看"


@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

@Composable
private fun ContinueWatching(recent: List<WatchRecord>, onResume: (WatchRecord) -> Unit) {
    if (recent.isEmpty()) {
        Message("还没有看到一半的节目")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CardWidth),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(recent, key = { it.videoKey }) { r ->
            PosterCard(r.title, r.imageUrl, "${r.episodeName} · ${formatTime(r.positionMs)}", onClick = { onResume(r) }, progress = r.progress)
        }
    }
}
