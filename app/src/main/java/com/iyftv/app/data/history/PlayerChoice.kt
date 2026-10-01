package com.iyftv.app.data.history

import android.content.Context

/**
 * Which player worked for each title, so it opens straight in that one next time.
 * Titles whose video servers refuse the app's player play in the website's player.
 */
class PlayerChoice(context: Context) {

    enum class Player { App, Web }

    private val prefs = context.getSharedPreferences("player_choice", Context.MODE_PRIVATE).also {
        // The app's player now asks for videos the way the website does; give it another try.
        if (it.getInt(VERSION, 0) < 2) it.edit().clear().putInt(VERSION, 2).apply()
    }

    fun get(videoKey: String): Player? =
        prefs.getString(videoKey, null)?.let { runCatching { Player.valueOf(it) }.getOrNull() }

    fun set(videoKey: String, player: Player) {
        if (get(videoKey) != player) prefs.edit().putString(videoKey, player.name).apply()
    }

    private companion object {
        const val VERSION = "__version"
    }
}
