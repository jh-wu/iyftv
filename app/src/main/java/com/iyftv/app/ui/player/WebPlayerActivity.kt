package com.iyftv.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.iyftv.app.IyfTvApp
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.iyf.IyfConfig
import com.iyftv.app.data.model.Episode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.ByteArrayInputStream

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
    private var videoKey = ""
    private var episodeKey = ""
    private var episodes: List<Episode> = emptyList()
    private var leaving = false

    private lateinit var controls: LinearLayout
    private lateinit var adLabel: TextView
    private lateinit var titleView: TextView
    private lateinit var timeView: TextView
    private lateinit var durationView: TextView
    private lateinit var progress: SeekBar
    private lateinit var rewindButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var playButton: ImageButton
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private var hideJob: Job? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        videoKey = intent.getStringExtra(EXTRA_VIDEO) ?: return finish()
        episodeKey = intent.getStringExtra(EXTRA_EPISODE) ?: return finish()
        startAt = intent.getLongExtra(EXTRA_START, 0L)
        intent.getStringExtra(EXTRA_NOTE)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            // The remote drives the app's control bar, not the page.
            isFocusable = false
            isFocusableInTouchMode = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = IyfConfig.USER_AGENT
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) = fullscreen()

                // Refuse the site's video ads, as an ad blocker does in a browser.
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (!isAd(request.url)) return null
                    adsBlocked++
                    return WebResourceResponse("video/mp4", null, 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            }
        }
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        controls = buildControls()
        adLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            text = "已跳过广告，马上继续播放…"
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(adLabel, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
            addView(controls, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        })
        web.loadUrl(IyfConfig.playPageUrl(videoKey, episodeKey))

        lifecycleScope.launch {
            episodes = runCatching { app.source.detail(videoKey).episodes }.getOrDefault(emptyList())
            updateEpisodeButtons()
        }

        job = lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000)
                tick++
                if (tick % 2 == 0) fullscreen()
                if (tick % 10 == 0) save(videoKey, episodeKey)
                refreshControls()
            }
        }
    }

    private fun buildControls(): LinearLayout {
        val dp = resources.displayMetrics.density
        fun button(icon: Int, label: String, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = label
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
            isFocusable = true
            setOnFocusChangeListener { v, focused ->
                (v.background as GradientDrawable).setColor(if (focused) 0x66FFFFFF else Color.TRANSPARENT)
            }
            setOnClickListener { onClick(); showControls() }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()).apply {
                marginStart = (12 * dp).toInt(); marginEnd = (12 * dp).toInt()
            }
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            text = listOf(intent.getStringExtra(EXTRA_TITLE), intent.getStringExtra(EXTRA_EPISODE_NAME))
                .filterNot { it.isNullOrBlank() }.joinToString(" ")
        }
        timeView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; text = "00:00" }
        durationView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; text = "00:00" }
        progress = SeekBar(this).apply {
            max = 1000
            // Left/right on the focused bar are handled in dispatchKeyEvent; touch isn't used.
            isFocusable = true
            setOnTouchListener { _, _ -> true }
            fun highlight(focused: Boolean) {
                thumb?.alpha = if (focused) 255 else 0
                progressTintList = android.content.res.ColorStateList.valueOf(if (focused) 0xFFFFB400.toInt() else Color.WHITE)
                scaleY = if (focused) 1.5f else 1f
            }
            highlight(false)
            setOnFocusChangeListener { _, focused -> highlight(focused) }
        }
        prevButton = button(androidx.media3.ui.R.drawable.exo_icon_previous, "上一集") { switchEpisode(-1) }
        playButton = button(androidx.media3.ui.R.drawable.exo_icon_pause, "播放/暂停") { togglePlay() }
        nextButton = button(androidx.media3.ui.R.drawable.exo_icon_next, "下一集") { switchEpisode(1) }
        rewindButton = button(androidx.media3.ui.R.drawable.exo_icon_rewind, "后退10秒") { seekBy(-10) }
        forwardButton = button(androidx.media3.ui.R.drawable.exo_icon_fastforward, "前进10秒") { seekBy(10) }
        updateEpisodeButtons()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((48 * dp).toInt(), (32 * dp).toInt(), (48 * dp).toInt(), (24 * dp).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE0000000.toInt(), 0x00000000))
            visibility = View.GONE
            addView(titleView)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(timeView)
                addView(progress, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    marginStart = (16 * dp).toInt(); marginEnd = (16 * dp).toInt()
                })
                addView(durationView)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (8 * dp).toInt() })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                listOf(prevButton, rewindButton, playButton, forwardButton, nextButton).forEach(::addView)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (8 * dp).toInt() })
        }
    }

    private fun updateEpisodeButtons() {
        if (!::prevButton.isInitialized) return
        val i = episodes.indexOfFirst { it.key == episodeKey }
        prevButton.visibility = if (i > 0) View.VISIBLE else View.GONE
        nextButton.visibility = if (i >= 0 && i + 1 < episodes.size) View.VISIBLE else View.GONE
    }

    private fun showControls() {
        if (controls.visibility != View.VISIBLE) {
            controls.visibility = View.VISIBLE
            playButton.requestFocus()
            refreshControls()
        }
        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(5_000)
            controls.visibility = View.GONE
        }
    }

    /** Runs [js] in the page; for tests. */
    fun runScript(js: String) = web.evaluateJavascript(js) { }

    /** True while the control bar is on screen. */
    val controlsShown get() = ::controls.isInitialized && controls.visibility == View.VISIBLE

    private fun hideControls() {
        hideJob?.cancel()
        controls.visibility = View.GONE
    }

    /** Reads the video's position and state into the control bar; moves on when an episode ends. */
    private fun refreshControls() {
        web.evaluateJavascript(
            "(function(){var v="+PICK+";return v?[v.currentTime,v.duration,v.paused,v.ended,!!window.__iyftvAd]:null})()",
        ) { result ->
            val arr = runCatching { JSONArray(result) }.getOrNull() ?: return@evaluateJavascript
            val t = arr.optDouble(0).takeIf { it.isFinite() } ?: 0.0
            val d = arr.optDouble(1).takeIf { it.isFinite() } ?: 0.0
            val paused = arr.optBoolean(2)
            val ended = arr.optBoolean(3)
            // The site's ad is hidden; say why the screen is briefly black.
            adLabel.visibility = if (arr.optBoolean(4)) View.VISIBLE else View.GONE
            // The episode itself (not the short ad before it) has finished.
            if (d >= 60 && (ended || t >= d - 0.5) && !leaving) {
                switchEpisode(1)
                return@evaluateJavascript
            }
            if (controls.visibility != View.VISIBLE) return@evaluateJavascript
            timeView.text = clock(t)
            durationView.text = clock(d)
            progress.progress = if (d > 0) (t / d * 1000).toInt() else 0
            playButton.setImageResource(
                if (paused) androidx.media3.ui.R.drawable.exo_icon_play else androidx.media3.ui.R.drawable.exo_icon_pause,
            )
        }
    }

    private var lastHoldSeek = 0L
    private var skipKeyHeld = false

    /**
     * Skips for a key press, and keeps skipping while it is held: 10s at a time,
     * five times a second, then 30s and 60s steps the longer the key is held.
     */
    private fun holdSeek(event: KeyEvent, direction: Int) {
        if (event.repeatCount > 0 && event.eventTime - lastHoldSeek < 200) return
        lastHoldSeek = event.eventTime
        val held = event.eventTime - event.downTime
        val step = when {
            held < 2_000 -> 10
            held < 5_000 -> 30
            else -> 60
        }
        seekBy(step * direction)
    }

    private fun clock(seconds: Double): String {
        val s = seconds.toLong()
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    private fun togglePlay() = web.evaluateJavascript(
        "(function(){var v="+PICK+";if(!v)return;if(v.paused){v.__iyftvPaused=false;v.play()}else{v.__iyftvPaused=true;v.pause()}})()",
    ) { refreshControls() }

    private fun seekBy(seconds: Int) = web.evaluateJavascript(
        "(function(){var v="+PICK+";if(v)v.currentTime=Math.max(0,v.currentTime+($seconds))})()",
    ) { refreshControls() }

    /** Opens the previous ([step] -1) or next (+1) episode in the website's player. */
    private fun switchEpisode(step: Int) {
        val i = episodes.indexOfFirst { it.key == episodeKey }
        val target = episodes.getOrNull(i + step) ?: run {
            if (step > 0 && i >= 0) { leaving = true; save(videoKey, episodeKey); finish() }
            return
        }
        if (i < 0 || leaving) return
        leaving = true
        save(videoKey, episodeKey)
        startActivity(
            Companion.intent(
                this, videoKey, target.key, 0, intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_IMAGE), target.name, null,
            ),
        )
        finish()
    }

    /** Lifts the page's video element over everything else and keeps it playing. */
    private fun fullscreen() {
        web.evaluateJavascript(FULLSCREEN_JS) { }
        web.evaluateJavascript(PROBE_JS) { lastState = it }
        if (!seeked && startAt > 0) {
            web.evaluateJavascript(
                "(function(){var v="+PICK+";if(v&&v.duration>${startAt / 1000 + 5}){v.currentTime=${startAt / 1000};return 1}return 0})()",
            ) { if (it == "1") seeked = true }
        }
    }

    private fun save(videoKey: String, episodeKey: String) {
        web.evaluateJavascript(
            "(function(){var v="+PICK+";return v?[v.currentTime,v.duration]:null})()",
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
        val code = event.keyCode
        // Media keys work the same whether or not the bar is showing.
        val media: (() -> Unit)? = when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> ::togglePlay
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { { seekBy(10) } }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { { seekBy(-10) } }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { { switchEpisode(1) } }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { { switchEpisode(-1) } }
            else -> null
        }
        if (media != null) {
            if (event.action == KeyEvent.ACTION_DOWN) { media(); showControls() }
            return true
        }
        if (controls.visibility == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) hideControls()
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) showControls()
            // A left/right press that opened the bar keeps skipping while held, instead of moving focus.
            if (skipKeyHeld && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.action == KeyEvent.ACTION_DOWN) holdSeek(event, if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                else skipKeyHeld = false
                return true
            }
            val focused = currentFocus
            val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
            // On the progress bar, left/right move through the video; holding the key speeds up.
            if (focused == progress && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.action == KeyEvent.ACTION_DOWN) holdSeek(event, if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                return true
            }
            // OK held on back/forward 10s keeps skipping until it is released.
            if (ok && (focused == rewindButton || focused == forwardButton)) {
                if (event.action == KeyEvent.ACTION_DOWN) holdSeek(event, if (focused == rewindButton) -1 else 1)
                return true
            }
            // Otherwise arrows move between the bar's buttons and OK presses one.
            return super.dispatchKeyEvent(event)
        }
        // Bar hidden: OK pauses, left/right skip (held: keep skipping), any of them also brings up the bar.
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                holdSeek(event, if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                if (event.repeatCount == 0) {
                    skipKeyHeld = true
                    showControls()
                }
            }
            return true
        }
        val action: () -> Unit = when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> ::togglePlay
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { {} }
            else -> null
        } ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { action(); showControls() }
        return true
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
        hideJob?.cancel()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    companion object {
        /** What the page looked like at the last check, for tests and error reports. */
        @Volatile var lastState: String? = null

        /** How many ad requests were refused; for tests. */
        @Volatile var adsBlocked = 0

        /** The site's 20s video ads (pre-roll and mid-episode) are mp4 clips on its ad CDN. */
        fun isAd(url: Uri): Boolean =
            url.host.orEmpty().endsWith("global-cdn.me") && url.path.orEmpty().endsWith(".mp4")

        /**
         * The episode's video element. The page holds several, including an empty
         * placeholder clip, so take the one that has real media loaded.
         */
        /**
         * The episode's video element. The page holds several (an empty placeholder clip,
         * ad clips), so take the one with real media loaded. Once an element has shown a
         * full-length video it stays the episode, so a short ad can never take its place.
         */
        private const val PICK = "(function(){var m=window.__iyftvMain;if(m&&m.isConnected)return m;" +
            "var b=null,bs=-1;[].forEach.call(document.querySelectorAll('video'),function(x){" +
            "var src=x.currentSrc||x.src||'';if(/empty\\d*\\.mp4/.test(src))return;" +
            "var sc=x.readyState*10+(src?5:0)+(isFinite(x.duration)&&x.duration>60?20:0)+(x.paused?0:1);" +
            "if(sc>bs){bs=sc;b=x}});" +
            "if(b&&isFinite(b.duration)&&b.duration>60&&b.readyState>=2){window.__iyftvMain=b}" +
            "return b})()"

        private val PROBE_JS = """
            (function(){
              var v=$PICK;
              var f=[].map.call(document.querySelectorAll('iframe'),function(x){return x.src}).slice(0,3);
              var r={url:location.href,title:document.title,videos:document.querySelectorAll('video').length,iframes:f};
              r.all=[].map.call(document.querySelectorAll('video'),function(x){return (x.currentSrc||x.src||'').slice(-50)+' rs'+x.readyState+' t'+x.currentTime.toFixed(1)+'/'+x.duration+(x.paused?' paused':'')+(x.muted?' muted':'')+' vol'+x.volume+(x===window.__iyftvMain?' MAIN':'')});
              var top=document.elementFromPoint(innerWidth/2,innerHeight/2);
              r.top=top?(top.tagName+'#'+top.id+'.'+String(top.className).slice(0,60)):null;
              r.fs=document.fullscreenElement?document.fullscreenElement.tagName:null;
              r.body=[].map.call(document.body.children,function(x){return x.tagName+'.'+String(x.className).slice(0,30)+(getComputedStyle(x).display==='none'?'(hidden)':'')}).slice(0,15);
              r.clean=document.documentElement.classList.contains('iyftv-clean');
              try{r.ls=Object.keys(localStorage).filter(function(k){return /vol|mute|sound|player|xg|dplayer|art/i.test(k)}).map(function(k){return k+'='+String(localStorage.getItem(k)).slice(0,80)})}catch(e){r.ls=String(e)}
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
              var v=$PICK; if(!v) return 'none';
              if(!document.getElementById('iyftv-style')){
                var s=document.createElement('style'); s.id='iyftv-style';
                s.textContent='html,body{overflow:hidden!important;background:#000!important}'+
                  'video.iyftv-full{position:fixed!important;left:0!important;top:0!important;width:100vw!important;'+
                  'height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483647!important;'+
                  'background:#000!important;object-fit:contain!important;transform:none!important}'+
                  'html.iyftv-clean body>:not(video.iyftv-full){display:none!important}'+
                  'html.iyftv-clean video.iyftv-full{display:block!important;visibility:visible!important;opacity:1!important}'+
                  'html.iyftv-clean video.iyftv-full.iyftv-ad{opacity:0!important}';
                document.head.appendChild(s);
              }
              if(v.parentNode!==document.body){ document.body.appendChild(v); }
              v.classList.add('iyftv-full');
              var main=window.__iyftvMain;
              if(main===v){
                // The episode is known: hide everything else on the page, so ads that pop
                // up over the video mid-way are never seen.
                document.documentElement.classList.add('iyftv-clean');
                if(document.fullscreenElement&&document.fullscreenElement!==v&&document.exitFullscreen) document.exitFullscreen();
                [].forEach.call(document.documentElement.children,function(x){
                  if(x!==document.head&&x!==document.body) x.style.setProperty('display','none','important');
                });
                // Ad clips elsewhere on the page are skipped to their end, so the site's
                // player moves straight back to the episode.
                [].forEach.call(document.querySelectorAll('video,audio'),function(x){
                  if(x===v) return;
                  x.classList.remove('iyftv-full');
                  if(!x.paused&&isFinite(x.duration)&&x.duration>0){ try{x.currentTime=x.duration}catch(e){} }
                });
                // Mid-way the site plays its ad clip (a plain .mp4) in the episode's own
                // element; the episode itself streams from a blob: URL. Hide and silence the
                // ad and skip it to its end the moment it loads.
                if(!v.__iyftvHooked){
                  v.__iyftvHooked=true;
                  var check=function(){
                    var ad=v.currentSrc&&v.currentSrc.indexOf('blob:')!==0;
                    v.classList.toggle('iyftv-ad',!!ad);
                    window.__iyftvAd=!!ad;
                    // The ad is skipped to its end, so it makes no sound and is never muted:
                    // the site saves the player's mute and volume and would bring them back
                    // on the episode. The episode always plays at full volume.
                    if(ad){
                      if(isFinite(v.duration)&&v.duration>0&&v.currentTime<v.duration-0.1){ try{v.currentTime=v.duration}catch(e){} }
                    }else{
                      if(v.muted) v.muted=false;
                      if(v.volume<1) v.volume=1;
                    }
                  };
                  ['loadstart','loadedmetadata','durationchange','play','playing','timeupdate'].forEach(function(e){ v.addEventListener(e,check); });
                  check();
                }
              }
              // Before the episode starts, the site plays the same short ad clip: skip it too.
              if(main!==v&&v.currentSrc&&v.currentSrc.indexOf('blob:')!==0&&isFinite(v.duration)&&v.duration<=60&&v.currentTime<v.duration-0.1){
                try{v.currentTime=v.duration}catch(e){}
              }
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
