package com.iyftv.app.data.iyf

/** Finds a playable stream URL by loading the site's own watch page. */
fun interface StreamSniffer {
    suspend fun sniff(pageUrl: String): String?
}
