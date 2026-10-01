package com.iyftv.app.data

import com.iyftv.app.data.model.Category
import com.iyftv.app.data.model.FilterGroup
import com.iyftv.app.data.model.ListFilter
import com.iyftv.app.data.model.Page
import com.iyftv.app.data.model.Stream
import com.iyftv.app.data.model.VideoDetail
import com.iyftv.app.data.model.VideoSummary

/**
 * Everything the UI needs from the content site. The UI only talks to this
 * interface, so the site-specific client can change without touching screens.
 */
interface VideoSource {
    suspend fun categories(): List<Category>
    suspend fun list(category: Category, page: Int, filter: ListFilter = emptyMap()): Page<VideoSummary>

    /** The filters a category list can be narrowed by. */
    fun filters(): List<FilterGroup> = emptyList()
    suspend fun search(query: String, page: Int): Page<VideoSummary>
    suspend fun detail(videoKey: String): VideoDetail
    suspend fun stream(videoKey: String, episodeKey: String): Stream
}
