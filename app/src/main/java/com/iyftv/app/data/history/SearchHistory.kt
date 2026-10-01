package com.iyftv.app.data.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Recent search terms, newest first, kept in SharedPreferences. */
class SearchHistory(context: Context) {

    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val _terms = MutableStateFlow(load())
    val terms: StateFlow<List<String>> = _terms.asStateFlow()

    fun add(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        save((listOf(t) + _terms.value.filterNot { it == t }).take(MAX))
    }

    fun clear() = save(emptyList())

    private fun load(): List<String> =
        runCatching { Json.decodeFromString<List<String>>(prefs.getString(KEY, null) ?: "[]") }.getOrDefault(emptyList())

    private fun save(terms: List<String>) {
        _terms.value = terms
        prefs.edit().putString(KEY, Json.encodeToString(terms)).apply()
    }

    private companion object {
        const val KEY = "terms"
        const val MAX = 20
    }
}
