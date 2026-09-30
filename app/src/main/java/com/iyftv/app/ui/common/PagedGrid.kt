package com.iyftv.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iyftv.app.data.model.Page
import com.iyftv.app.data.model.VideoSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GridState(
    val items: List<VideoSummary> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

/** Loads pages of titles on demand as the grid scrolls. */
open class PagedGridViewModel(
    private var loader: (suspend (page: Int) -> Page<VideoSummary>)?,
) : ViewModel() {

    private val _state = MutableStateFlow(GridState())
    val state: StateFlow<GridState> = _state.asStateFlow()
    private var nextPage = 1
    private var job: Job? = null

    init { if (loader != null) loadMore() }

    protected fun reset(newLoader: (suspend (Int) -> Page<VideoSummary>)?) {
        job?.cancel()
        loader = newLoader
        nextPage = 1
        _state.value = GridState(hasMore = newLoader != null)
        if (newLoader != null) loadMore()
    }

    fun loadMore() {
        val load = loader ?: return
        val s = _state.value
        if (s.loading || !s.hasMore) return
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            runCatching { load(nextPage) }
                .onSuccess { page ->
                    nextPage++
                    _state.update { st ->
                        st.copy(
                            items = (st.items + page.items).distinctBy { it.key },
                            loading = false,
                            hasMore = page.hasMore && page.items.isNotEmpty(),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }
}

@Composable
fun VideoGrid(
    state: GridState,
    onOpen: (VideoSummary) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "没有内容",
) {
    if (state.items.isEmpty()) {
        when {
            state.error != null -> Message("加载失败：${state.error}", onRetry = onLoadMore)
            state.loading -> Message("加载中…")
            else -> Message(emptyText)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CardWidth),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        itemsIndexed(state.items, key = { _, v -> v.key }) { index, v ->
            if (index >= state.items.size - 12) {
                LaunchedEffect(state.items.size) { onLoadMore() }
            }
            PosterCard(v.title, v.imageUrl, v.subtitle, onClick = { onOpen(v) })
        }
        if (state.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { Message("加载失败：${state.error}", onRetry = onLoadMore) }
        }
    }
}
