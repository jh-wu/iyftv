package com.iyftv.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.WatchHistoryDao
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.VideoDetail
import com.iyftv.app.ui.common.Load
import com.iyftv.app.ui.common.Message
import com.iyftv.app.ui.common.formatTime
import com.iyftv.app.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val source: VideoSource,
    history: WatchHistoryDao,
    private val videoKey: String,
) : ViewModel() {
    private val _detail = MutableStateFlow<Load<VideoDetail>>(Load.Loading)
    val detail: StateFlow<Load<VideoDetail>> = _detail.asStateFlow()

    val record: StateFlow<WatchRecord?> =
        history.observe(videoKey).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load() {
        _detail.value = Load.Loading
        viewModelScope.launch {
            _detail.value = runCatching { source.detail(videoKey) }
                .fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })
        }
    }
}

@Composable
fun DetailScreen(vm: DetailViewModel, onPlay: (episodeKey: String?) -> Unit) {
    val state by vm.detail.collectAsState()
    val record by vm.record.collectAsState()
    when (val s = state) {
        is Load.Loading -> Message("加载中…")
        is Load.Failed -> Message("加载失败：${s.message}", onRetry = vm::load)
        is Load.Ready -> DetailContent(s.value, record, onPlay)
    }
}

@Composable
private fun DetailContent(d: VideoDetail, record: WatchRecord?, onPlay: (String?) -> Unit) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(d.key) { runCatching { playFocus.requestFocus() } }
    val resumable = record?.takeIf { !it.isFinished && d.episodes.any { e -> e.key == it.episodeKey } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                AsyncImage(
                    model = d.imageUrl,
                    contentDescription = d.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(220.dp).aspectRatio(2f / 3f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(d.title, style = MaterialTheme.typography.headlineMedium)
                    d.meta?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    d.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (resumable != null) {
                            Button(onClick = { onPlay(resumable.episodeKey) }, modifier = Modifier.focusRequester(playFocus)) {
                                Text("继续播放 ${resumable.episodeName} ${formatTime(resumable.positionMs)}")
                            }
                            OutlinedButton(onClick = { onPlay(d.episodes.firstOrNull()?.key) }) { Text("从头播放") }
                        } else if (d.episodes.isNotEmpty()) {
                            Button(onClick = { onPlay(d.episodes.first().key) }, modifier = Modifier.focusRequester(playFocus)) {
                                Text("播放")
                            }
                        }
                    }
                }
            }
        }
        if (d.episodes.size > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("选集", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            }
            items(d.episodes, key = { it.key }) { ep ->
                val current = ep.key == record?.episodeKey
                if (current) {
                    Button(onClick = { onPlay(ep.key) }) { Text(ep.name, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { onPlay(ep.key) }) { Text(ep.name, maxLines = 1) }
                }
            }
        }
    }
}
