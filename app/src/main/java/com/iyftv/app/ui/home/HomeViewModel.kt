package com.iyftv.app.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.PlayerChoice
import com.iyftv.app.data.history.WatchHistoryDao
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val source: VideoSource,
    private val history: WatchHistoryDao,
    private val prefs: SharedPreferences,
    private val playerChoice: PlayerChoice,
) : ViewModel() {

    /** The open tab (0 is 继续观看, then the categories), kept across visits to a title and app restarts. */
    private val _selectedTab = MutableStateFlow(prefs.getInt(KEY_TAB, 1))
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        if (_selectedTab.value == index) return
        _selectedTab.value = index
        prefs.edit().putInt(KEY_TAB, index).apply()
    }


    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    val continueWatching: StateFlow<List<WatchRecord>> =
        history.recent(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The player remembered for a title, or null when the app will test again. */
    fun playerFor(videoKey: String): PlayerChoice.Player? = playerChoice.get(videoKey)

    fun resetPlayer(videoKey: String) = playerChoice.clear(videoKey)

    fun removeFromHistory(videoKey: String) {
        viewModelScope.launch { history.delete(videoKey) }
    }

    private val posterTried = mutableMapOf<String, MutableSet<String?>>()
    private val _posterNotes = MutableStateFlow<Map<String, String>>(emptyMap())

    /** What happened to each title's poster this session, shown in its long-press menu. */
    val posterNotes: StateFlow<Map<String, String>> = _posterNotes.asStateFlow()

    private fun note(videoKey: String, line: String) {
        _posterNotes.value = _posterNotes.value + (videoKey to listOfNotNull(_posterNotes.value[videoKey], line).joinToString("\n"))
    }

    /**
     * Titles watched long ago can keep a poster address that no longer loads (or none at all).
     * When one fails, look for another address: the title's page first, then the search result
     * for its name, and save the first one not tried yet.
     */
    fun refreshPoster(videoKey: String, title: String, failedUrl: String?, error: String) {
        val tried = posterTried.getOrPut(videoKey) { mutableSetOf() }
        if (!tried.add(failedUrl)) return
        note(videoKey, "海报加载失败：${failedUrl ?: "没有地址"}（$error）")
        viewModelScope.launch {
            val next = fromDetail(videoKey)?.let { if (it in tried) { note(videoKey, "详情页的海报就是这个地址"); null } else it }
                ?: fromSearch(videoKey, title)?.takeIf { it !in tried }
            if (next == null) {
                note(videoKey, "没有找到其他海报地址")
                return@launch
            }
            note(videoKey, "改用：$next")
            val record = history.get(videoKey) ?: return@launch
            history.upsert(record.copy(imageUrl = next))
        }
    }

    private suspend fun fromDetail(videoKey: String): String? =
        runCatching { source.detail(videoKey).imageUrl }
            .onSuccess { if (it == null) note(videoKey, "详情页没有海报") }
            .onFailure { note(videoKey, "详情页读取失败：${it.message}") }
            .getOrNull()

    private suspend fun fromSearch(videoKey: String, title: String): String? =
        runCatching {
            val hits = source.search(title, 1).items
            (hits.firstOrNull { it.key == videoKey } ?: hits.firstOrNull { it.title == title })?.imageUrl
        }
            .onSuccess { if (it == null) note(videoKey, "搜索结果里没有这部的海报") }
            .onFailure { note(videoKey, "搜索失败：${it.message}") }
            .getOrNull()

    init {
        viewModelScope.launch { _categories.value = source.categories() }
    }

    private companion object {
        const val KEY_TAB = "selected_tab_v2"
    }
}
