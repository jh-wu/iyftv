package com.iyftv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import android.os.SystemClock
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
    onSettings: () -> Unit,
) {
    val categories by vm.categories.collectAsState()
    val allRecent by vm.continueWatching.collectAsState()
    val recent = allRecent.filterNot { it.isFinished }
    val selected by vm.selectedTab.collectAsState()
    // Coming back from a title, focus returns to the tab that was open, so it stays selected.
    val tabFocus = remember { FocusRequester() }
    var listClosedAt by remember { mutableLongStateOf(0L) }

    Column(Modifier.fillMaxSize()) {
        // One row: 继续观看 as an icon tab, the six categories, then search and settings.
        Row(
            Modifier.fillMaxWidth().padding(start = 40.dp, end = 48.dp, top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (categories.isNotEmpty()) {
                val current = selected.coerceIn(0..categories.size)
                TabRow(selectedTabIndex = current) {
                    (0..categories.size).forEach { i ->
                        Tab(
                            selected = i == current,
                            // Focus that lands on a tab right after a sort/filter list closes is
                            // the list handing focus back, not the user changing tabs.
                            onFocus = { if (SystemClock.uptimeMillis() - listClosedAt > 1_000) vm.selectTab(i) },
                            onClick = { vm.selectTab(i) },
                            modifier = if (i == current) Modifier.focusRequester(tabFocus) else Modifier,
                        ) {
                            if (i == 0) {
                                Icon(
                                    AppIcons.History,
                                    contentDescription = CONTINUE_TAB,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).size(24.dp),
                                )
                            } else {
                                Text(categories[i - 1].name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
            HeaderButton(Icons.Default.Search, "搜索", onSearch)
            HeaderButton(Icons.Default.Settings, "设置", onSettings)
        }
        if (categories.isEmpty()) return@Column
        val current = selected.coerceIn(0..categories.size)

        LaunchedEffect(Unit) { runCatching { tabFocus.requestFocus() } }

        if (current == 0) {
            ContinueWatching(recent, onResume)
            return@Column
        }
        val category = categories[current - 1]
        key(category.id) {
            val gridVm = categoryViewModel(category)
            val state by gridVm.state.collectAsState()
            FilterBar(gridVm, onListClosed = { listClosedAt = SystemClock.uptimeMillis() })
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
