package com.iyftv.app

import android.app.Application
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.AppDatabase
import com.iyftv.app.data.history.SearchHistory
import com.iyftv.app.data.history.WatchHistoryDao
import com.iyftv.app.data.iyf.IyfVideoSource
import com.iyftv.app.data.iyf.WebViewCookieJar
import com.iyftv.app.data.iyf.WebViewKeyFetcher
import com.iyftv.app.data.iyf.WebViewStreamSniffer
import com.iyftv.app.data.update.UpdateChecker
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IyfTvApp : Application() {

    lateinit var http: OkHttpClient
        private set
    lateinit var source: VideoSource
        private set
    lateinit var history: WatchHistoryDao
        private set
    lateinit var updates: UpdateChecker
        private set
    lateinit var searchHistory: SearchHistory
        private set

    override fun onCreate() {
        super.onCreate()
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cookieJar(WebViewCookieJar())
            .build()
        source = IyfVideoSource(http, WebViewStreamSniffer(this), WebViewKeyFetcher(this))
        history = AppDatabase.create(this).watchHistory()
        searchHistory = SearchHistory(this)
        updates = UpdateChecker(
            http.newBuilder().cookieJar(CookieJar.NO_COOKIES).readTimeout(60, TimeUnit.SECONDS).build(),
            BuildConfig.VERSION_CODE,
        )
    }
}
