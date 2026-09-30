package com.iyftv.app.data.iyf

/** Reads the API keys by loading the homepage in a real browser engine. */
fun interface BrowserKeyFetcher {
    suspend fun fetch(pageUrl: String): IyfKeys?
}
