package com.iyftv.app

import android.app.Application
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.AppDatabase
import com.iyftv.app.data.history.WatchHistoryDao
import com.iyftv.app.data.iyf.IyfVideoSource
import com.iyftv.app.data.iyf.WebViewStreamSniffer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IyfTvApp : Application() {

    lateinit var http: OkHttpClient
        private set
    lateinit var source: VideoSource
        private set
    lateinit var history: WatchHistoryDao
        private set

    override fun onCreate() {
        super.onCreate()
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        source = IyfVideoSource(http, WebViewStreamSniffer(this))
        history = AppDatabase.create(this).watchHistory()
    }
}
