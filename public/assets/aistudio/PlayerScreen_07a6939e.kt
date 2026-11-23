package com.plustv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: TvChannel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLandscape by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // Build custom headers (Cookie, User-Agent, Referer, Origin) — works natively on Android!
    val requestHeaders = remember(channel) {
        val map = LinkedHashMap<String, String>()
        channel.headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true)) {
                map[k] = v
            }
        }
        if (channel.streamUrl.contains(".cdn.jio.com", ignoreCase = true)) {
            map.putIfAbsent("Referer", "https://www.jiotv.com/")
            map.putIfAbsent("Origin", "https://www.jiotv.com")
        }
        map
    }

    val userAgent = remember(channel) {
        channel.headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: "PlayTV/7.1.5 (Linux;Android 8.1.0) ExoPlayerLib/2.11.7"
    }

    // Also attach __hdnea__ query token if present in Cookie (works for both _MOB & _BTS)
    val finalStreamUrl = remember(channel) {
        val cookie = channel.headers["Cookie"] ?: channel.headers["cookie"] ?: ""
        val match = Regex("__hdnea__=[^;\\s]+").find(cookie)
        if (match != null && !channel.streamUrl.contains("__hdnea__=")) {
            val sep = if (channel.streamUrl.contains("?")) "&" else "?"
            channel.streamUrl + sep + match.value
        } else {
            channel.streamUrl
        }
    }

    val exoPlayer = remember(channel.id, reloadKey) {
        errorMessage = null

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(25_000)
            .setDefaultRequestProperties(requestHeaders)

        val drmSessionManager = if (channel.clearKeys.isNotEmpty()) {
            val jwkBytes = M3uParser.buildClearKeyJwkBytes(channel.clearKeys)
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(
                    C.CLEARKEY_UUID,
                    FrameworkMediaDrm.DEFAULT_PROVIDER
                )
                .build(LocalMediaDrmCallback(jwkBytes))
        } else {
            null
        }

        val mediaItem = MediaItem.Builder()
            .setUri(finalStreamUrl)
            .apply {
                if (channel.isDash) setMimeType(MimeTypes.APPLICATION_MPD)
                else if (finalStreamUrl.contains(".m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        val mediaSource = when {
            channel.isDash -> DashMediaSource.Factory(httpFactory)
                .apply { drmSessionManager?.let { setDrmSessionManagerProvider { it } } }
                .createMediaSource(mediaItem)

            finalStreamUrl.contains(".m3u8", ignoreCase = true) -> HlsMediaSource.Factory(httpFactory)
                .apply { drmSessionManager?.let { setDrmSessionManagerProvider { it } } }
                .createMediaSource(mediaItem)

            else -> DefaultMediaSourceFactory(httpFactory)
                .apply { drmSessionManager?.let { setDrmSessionManagerProvider { it } } }
                .createMediaSource(mediaItem)
        }

        val trackSelector = DefaultTrackSelector(context)
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                setMediaSource(mediaSource)
                playWhenReady = true
                prepare()
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "Playback Error (${error.errorCodeName}): ${error.message ?: "Stream unavailable"}"
                    }
                })
            }
    }

    // Manage fullscreen system bars & orientation cleanup
    DisposableEffect(isLandscape) {
        activity?.let { act ->
            act.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isLandscape) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Player Bar (hidden in landscape fullscreen)
        if (!isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${channel.group} · ${if (channel.isDash) "DASH" else "HLS"}${if (channel.clearKeys.isNotEmpty()) " · ClearKey" else ""}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                ) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Fit mode", tint = Color.White)
                }
                IconButton(onClick = { isLandscape = !isLandscape }) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                }
            }
        }

        // Video Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = resizeMode
                }
            )

            errorMessage?.let { msg ->
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .background(Color(0xEE11131A), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { reloadKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Stream")
                    }
                }
            }
        }
    }
}
