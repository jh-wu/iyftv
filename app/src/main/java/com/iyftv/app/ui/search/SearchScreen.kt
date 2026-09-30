package com.iyftv.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.iyftv.app.data.VideoSource
import com.iyftv.app.ui.common.PagedGridViewModel
import com.iyftv.app.ui.common.VideoGrid

class SearchViewModel(private val source: VideoSource) : PagedGridViewModel(null) {
    var lastQuery = ""
        private set

    fun search(query: String) {
        val q = query.trim()
        if (q == lastQuery) return
        lastQuery = q
        reset(if (q.isEmpty()) null else { page -> source.search(q, page) })
    }
}

@Composable
fun SearchScreen(vm: SearchViewModel, onOpenVideo: (String) -> Unit) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            modifier = Modifier
                .padding(start = 48.dp, top = 32.dp)
                .width(600.dp)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        VideoGrid(
            state,
            onOpen = { onOpenVideo(it.key) },
            onLoadMore = vm::loadMore,
            emptyText = if (vm.lastQuery.isEmpty()) "输入片名后按搜索" else "没有找到结果",
        )
    }
}
