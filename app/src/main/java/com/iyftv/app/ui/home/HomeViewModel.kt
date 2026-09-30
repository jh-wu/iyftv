package com.iyftv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.WatchHistoryDao
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.Category
import com.iyftv.app.data.model.VideoSummary
import com.iyftv.app.ui.common.Load
import com.iyftv.app.ui.common.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryRow(val category: Category, val videos: List<VideoSummary>)

class HomeViewModel(
    private val source: VideoSource,
    history: WatchHistoryDao,
) : ViewModel() {

    private val _rows = MutableStateFlow<Load<List<CategoryRow>>>(Load.Loading)
    val rows: StateFlow<Load<List<CategoryRow>>> = _rows.asStateFlow()

    val continueWatching: StateFlow<List<WatchRecord>> =
        history.recent(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { load() }

    fun load() {
        _rows.value = Load.Loading
        viewModelScope.launch {
            _rows.value = runCatching {
                source.categories()
                    .map { c -> async { runCatching { CategoryRow(c, source.list(c, 1).items) }.getOrNull() } }
                    .awaitAll()
                    .filterNotNull()
                    .filter { it.videos.isNotEmpty() }
                    .ifEmpty { error("无法加载内容") }
            }.fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })
        }
    }
}
