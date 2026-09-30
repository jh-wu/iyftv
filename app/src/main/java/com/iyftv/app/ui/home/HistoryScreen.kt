package com.iyftv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iyftv.app.data.history.WatchHistoryDao
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.ui.common.CardWidth
import com.iyftv.app.ui.common.Message
import com.iyftv.app.ui.common.PosterCard
import com.iyftv.app.ui.common.formatTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(history: WatchHistoryDao) : ViewModel() {
    val records: StateFlow<List<WatchRecord>> =
        history.recent(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, onOpen: (WatchRecord) -> Unit) {
    val records by vm.records.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Text("观看记录", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 48.dp, top = 32.dp))
        if (records.isEmpty()) {
            Message("还没有观看记录")
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CardWidth),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(records, key = { it.videoKey }) { r ->
                val sub = if (r.isFinished) "${r.episodeName} · 已看完" else "${r.episodeName} · ${formatTime(r.positionMs)}"
                PosterCard(r.title, r.imageUrl, sub, onClick = { onOpen(r) }, progress = r.progress)
            }
        }
    }
}
