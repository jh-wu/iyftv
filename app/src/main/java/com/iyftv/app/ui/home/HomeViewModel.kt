package com.iyftv.app.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.PlayerChoice
import com.iyftv.app.data.iyf.IyfConfig
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

    /**
     * Titles watched long ago can keep a poster address that no longer loads (or none at all).
     * When one fails, look for another address: the full-size copy of a thumbnail, the title's
     * page, then the search result for its name, and save the first one not tried yet.
     */
    fun refreshPoster(videoKey: String, title: String, failedUrl: String?) {
        val tried = posterTried.getOrPut(videoKey) { mutableSetOf() }
        if (!tried.add(failedUrl)) return
        viewModelScope.launch {
            val next = failedUrl?.let(IyfConfig::fullSizePoster)?.takeIf { it !in tried }
                ?: runCatching { source.detail(videoKey).imageUrl }.getOrNull()?.takeIf { it !in tried }
                ?: runCatching {
                    val hits = source.search(title, 1).items
                    (hits.firstOrNull { it.key == videoKey } ?: hits.firstOrNull { it.title == title })?.imageUrl
                }.getOrNull()?.takeIf { it !in tried }
                ?: return@launch
            val record = history.get(videoKey) ?: return@launch
            history.upsert(record.copy(imageUrl = next))
        }
    }

    init {
        viewModelScope.launch { _categories.value = source.categories() }
    }

    private companion object {
        const val KEY_TAB = "selected_tab_v2"
    }
}
