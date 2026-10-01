package com.iyftv.app.data.model

/** A browsable section of the catalog, e.g. movies or TV series. */
data class Category(val id: String, val name: String)

/** A title as it appears in a row, grid or search result. */
data class VideoSummary(
    val key: String,
    val title: String,
    val imageUrl: String?,
    val subtitle: String? = null,
)

data class Episode(val key: String, val name: String)

data class VideoDetail(
    val key: String,
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val meta: String?,
    val episodes: List<Episode>,
)

data class Page<T>(val items: List<T>, val hasMore: Boolean)

/** A resolved, directly playable stream. */
/** A playable URL, plus other URLs for the same episode to fall back to if it fails. */
data class Stream(val url: String, val headers: Map<String, String> = emptyMap(), val alternates: List<String> = emptyList())
