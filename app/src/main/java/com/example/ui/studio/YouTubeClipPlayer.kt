package com.example.ui.studio

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeClipWebView(
    videoId: String,
    startSeconds: Long,
    endSeconds: Long,
    isPlaying: Boolean,
    seekSignal: Int,
    seekTargetOffsetSec: Double,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    val embedHtml = remember(videoId, startSeconds, endSeconds) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; height: 100%; background: #0A0B10; overflow: hidden; }
            #player { width: 100%; height: 100%; pointer-events: none; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player;
            var clipStart = $startSeconds;
            var clipEnd = $endSeconds;
            var isReady = false;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                videoId: '$videoId',
                playerVars: {
                  'playsinline': 1,
                  'controls': 0,
                  'rel': 0,
                  'modestbranding': 1,
                  'start': clipStart,
                  'end': clipEnd
                },
                events: {
                  'onReady': function() {
                    isReady = true;
                    player.seekTo(clipStart, true);
                  },
                  'onStateChange': function(e) {
                    if (e.data === YT.PlayerState.ENDED) {
                      player.seekTo(clipStart, true);
                      player.playVideo();
                    }
                  }
                }
              });
            }
            function cpPlay() { if (isReady && player) player.playVideo(); }
            function cpPause() { if (isReady && player) player.pauseVideo(); }
            function cpSeek(offsetSec) {
              if (isReady && player) {
                player.seekTo(clipStart + offsetSec, true);
              }
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    LaunchedEffect(isPlaying, pageReady) {
        if (pageReady) {
            val cmd = if (isPlaying) "cpPlay()" else "cpPause()"
            webViewRef?.evaluateJavascript(cmd, null)
        }
    }

    LaunchedEffect(seekSignal, pageReady) {
        if (pageReady && seekSignal > 0) {
            webViewRef?.evaluateJavascript("cpSeek($seekTargetOffsetSec)", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(modifier = modifier.background(Color(0xFF0A0B10))) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    setBackgroundColor(android.graphics.Color.parseColor("#0A0B10"))
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageReady = true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            return true
                        }
                    }
                    loadDataWithBaseURL(
                        "https://www.youtube.com",
                        embedHtml,
                        "text/html",
                        "utf-8",
                        null
                    )
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
