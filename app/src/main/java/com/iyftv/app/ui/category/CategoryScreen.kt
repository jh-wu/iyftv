package com.iyftv.app.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.model.Category
import com.iyftv.app.ui.common.PagedGridViewModel
import com.iyftv.app.ui.common.VideoGrid

class CategoryViewModel(source: VideoSource, category: Category) :
    PagedGridViewModel({ page -> source.list(category, page) })

@Composable
fun CategoryScreen(title: String, vm: CategoryViewModel, onOpenVideo: (String) -> Unit) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 48.dp, top = 32.dp))
        VideoGrid(state, onOpen = { onOpenVideo(it.key) }, onLoadMore = vm::loadMore)
    }
}
