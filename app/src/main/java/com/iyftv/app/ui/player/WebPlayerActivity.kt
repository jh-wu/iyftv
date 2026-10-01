package com.iyftv.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.iyftv.app.IyfTvApp
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.iyf.IyfConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Plays an episode with the website's own player in a full-screen WebView. Used when the
 * video servers refuse the app's player but still serve a browser. Remote keys control
 * the page's video element; progress goes into watch history like the normal player.
 */
class WebPlayerActivity : ComponentActivity() {

    private val app get() = application as IyfTvApp
    private lateinit var web: WebView
    private var job: Job? = null
    private var startAt = 0L
    private var seeked = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val videoKey = intent.getStringExtra(EXTRA_VIDEO) ?: return finish()
        val episodeKey = intent.getStringExtra(EXTRA_EPISODE) ?: return finish()
        startAt = intent.getLongExtra(EXTRA_START, 0L)
        intent.getStringExtra(EXTRA_NOTE)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }

        web = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = IyfConfig.USER_AGENT
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) = fullscreen()
            }
        }
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        setContentView(web)
        web.loadUrl(IyfConfig.playPageUrl(videoKey, episodeKey))

        job = lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                delay(2_000)
                fullscreen()
                if (++tick % 5 == 0) save(videoKey, episodeKey)
            }
        }
    }

    /** Lifts the page's video element over everything else and keeps it playing. */
    private fun fullscreen() {
        web.evaluateJavascript(FULLSCREEN_JS) { }
        web.evaluateJavascript(PROBE_JS) { lastState = it }
        if (!seeked && startAt > 0) {
            web.evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v&&v.duration>${startAt / 1000 + 5}){v.currentTime=${startAt / 1000};return 1}return 0})()",
            ) { if (it == "1") seeked = true }
        }
    }

    private fun save(videoKey: String, episodeKey: String) {
        web.evaluateJavascript(
            "(function(){var v=document.querySelector('video');return v?[v.currentTime,v.duration]:null})()",
        ) { result ->
            val arr = runCatching { JSONArray(result) }.getOrNull() ?: return@evaluateJavascript
            val position = (arr.optDouble(0) * 1000).toLong()
            val duration = (arr.optDouble(1).takeIf { !it.isNaN() && !it.isInfinite() } ?: 0.0).times(1000).toLong()
            // The site plays a short ad first; only record the real episode.
            if (duration < 60_000 || position <= 0) return@evaluateJavascript
            lifecycleScope.launch {
                app.history.upsert(
                    WatchRecord(
                        videoKey = videoKey,
                        title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                        imageUrl = intent.getStringExtra(EXTRA_IMAGE),
                        episodeKey = episodeKey,
                        episodeName = intent.getStringExtra(EXTRA_EPISODE_NAME).orEmpty(),
                        positionMs = position,
                        durationMs = duration,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val js = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE ->
                "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused){v.__iyftvPaused=false;v.play()}else{v.__iyftvPaused=true;v.pause()}})()"
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                "(function(){var v=document.querySelector('video');if(v)v.currentTime+=10})()"
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND ->
                "(function(){var v=document.querySelector('video');if(v)v.currentTime-=10})()"
            else -> null
        }
        if (js != null) {
            web.evaluateJavascript(js) { }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        val videoKey = intent.getStringExtra(EXTRA_VIDEO)
        val episodeKey = intent.getStringExtra(EXTRA_EPISODE)
        if (videoKey != null && episodeKey != null) save(videoKey, episodeKey)
        web.onPause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (::web.isInitialized) web.onResume()
    }

    override fun onDestroy() {
        job?.cancel()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    companion object {
        /** What the page looked like at the last check, for tests and error reports. */
        @Volatile var lastState: String? = null

        private val PROBE_JS = """
            (function(){
              var v=document.querySelector('video');
              var f=[].map.call(document.querySelectorAll('iframe'),function(x){return x.src}).slice(0,3);
              var r={url:location.href,title:document.title,videos:document.querySelectorAll('video').length,iframes:f};
              if(v){r.src=(v.currentSrc||v.src||'').slice(0,120);r.paused=v.paused;r.t=v.currentTime;r.d=String(v.duration);
                r.ready=v.readyState;r.net=v.networkState;r.err=v.error?v.error.code+' '+v.error.message:null;}
              return JSON.stringify(r);
            })()
        """.trimIndent()

        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_EPISODE = "episode"
        private const val EXTRA_START = "start"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_IMAGE = "image"
        private const val EXTRA_EPISODE_NAME = "episodeName"
        private const val EXTRA_NOTE = "note"

        private val FULLSCREEN_JS = """
            (function(){
              var v=document.querySelector('video'); if(!v) return 'none';
              if(!document.getElementById('iyftv-style')){
                var s=document.createElement('style'); s.id='iyftv-style';
                s.textContent='html,body{overflow:hidden!important;background:#000!important}'+
                  'video.iyftv-full{position:fixed!important;left:0!important;top:0!important;width:100vw!important;'+
                  'height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483647!important;'+
                  'background:#000!important;object-fit:contain!important;transform:none!important}';
                document.head.appendChild(s);
              }
              if(v.parentNode!==document.body){ document.body.appendChild(v); }
              v.classList.add('iyftv-full');
              if(v.paused && !v.__iyftvPaused){ var p=v.play(); if(p&&p.catch) p.catch(function(){}); }
              return 'ok';
            })()
        """.trimIndent()

        fun intent(
            context: Context, videoKey: String, episodeKey: String, startMs: Long,
            title: String, imageUrl: String?, episodeName: String, note: String?,
        ) = Intent(context, WebPlayerActivity::class.java)
            .putExtra(EXTRA_VIDEO, videoKey)
            .putExtra(EXTRA_EPISODE, episodeKey)
            .putExtra(EXTRA_START, startMs)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_IMAGE, imageUrl)
            .putExtra(EXTRA_EPISODE_NAME, episodeName)
            .putExtra(EXTRA_NOTE, note)
    }
}
