package com.iyftv.app.ui.category

import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.model.Category
import com.iyftv.app.data.model.FilterGroup
import com.iyftv.app.data.model.ListFilter
import com.iyftv.app.ui.common.PagedGridViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One category tab on the home screen, narrowed by the chosen filters. */
class CategoryViewModel(private val source: VideoSource, private val category: Category) :
    PagedGridViewModel({ page -> source.list(category, page) }) {

    val filterGroups: List<FilterGroup> = source.filters()

    private val _filter = MutableStateFlow<ListFilter>(emptyMap())
    val filter: StateFlow<ListFilter> = _filter.asStateFlow()

    /** Sets one filter ([value] null for all) and reloads the list from the first page. */
    fun setFilter(groupId: String, value: String?) {
        val next = if (value == null) _filter.value - groupId else _filter.value + (groupId to value)
        if (next == _filter.value) return
        _filter.value = next
        reset { page -> source.list(category, page, next) }
    }
}
