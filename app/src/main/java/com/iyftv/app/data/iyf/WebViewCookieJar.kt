package com.iyftv.app.data.iyf

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Shares cookies between OkHttp and WebView, so a check passed in one counts in the other. */
class WebViewCookieJar : CookieJar {
    private val manager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        manager.getCookie(url.toString())
            ?.split(";")
            ?.mapNotNull { Cookie.parse(url, it.trim()) }
            .orEmpty()
}
