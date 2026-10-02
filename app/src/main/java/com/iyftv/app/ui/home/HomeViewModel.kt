package com.iyftv.app.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.WatchHistoryDao
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(source: VideoSource, history: WatchHistoryDao, private val prefs: SharedPreferences) : ViewModel() {

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

    init {
        viewModelScope.launch { _categories.value = source.categories() }
    }

    private companion object {
        const val KEY_TAB = "selected_tab_v2"
    }
}
