package com.iyftv.app.data.history

import android.content.Context

/**
 * Which player worked for each title, so it opens straight in that one next time.
 * Titles whose video servers refuse the app's player play in the website's player.
 */
class PlayerChoice(context: Context) {

    enum class Player { App, Web }

    private val prefs = context.getSharedPreferences("player_choice", Context.MODE_PRIVATE)

    fun get(videoKey: String): Player? =
        prefs.getString(videoKey, null)?.let { runCatching { Player.valueOf(it) }.getOrNull() }

    fun set(videoKey: String, player: Player) {
        if (get(videoKey) != player) prefs.edit().putString(videoKey, player.name).apply()
    }
}
