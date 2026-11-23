package com.example.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.M3uParser
import com.example.data.TvChannel
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOnSurfaceMuted
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTileStage
import com.example.ui.theme.ErrorSoft
import com.example.ui.theme.PlusTvBrandGradient
import com.example.ui.theme.StarGold

/**
 * Wraps an HttpDataSource so every manifest, sub-playlist, and media segment request
 * carries the channel's __hdnea__ query auth token (when present) in addition to Cookie headers.
 * Many IPTV/Akamai CDNs validate __hdnea__ on child .m4s/.ts segments, not just the initial manifest.
 */
@UnstableApi
private class AuthTokenDataSource(
    private val upstream: HttpDataSource,
    private val hdneaQueryToken: String?
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val originalUri = dataSpec.uri
        val rawStr = originalUri.toString()
        val updatedSpec = if (
            !hdneaQueryToken.isNullOrBlank() &&
            !rawStr.contains("__hdnea__=") &&
            (rawStr.startsWith("http://", ignoreCase = true) ||
                rawStr.startsWith("https://", ignoreCase = true))
        ) {
            val sep = if (rawStr.contains("?")) "&" else "?"
            dataSpec.withUri(Uri.parse(rawStr + sep + hdneaQueryToken))
        } else {
            dataSpec
        }
        return upstream.open(updatedSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
    }
}

@UnstableApi
private fun buildReadableErrorMessage(
    error: PlaybackException,
    streamUrl: String
): Pair<String, String?> {
    val rootCause = generateSequence<Throwable>(error) { it.cause }.last()
    val httpInvalid = generateSequence<Throwable>(error) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()

    if (httpInvalid != null) {
        val code = httpInvalid.responseCode
        val isJioCdn = streamUrl.contains("jio.com", ignoreCase = true) ||
            streamUrl.contains("jiotv", ignoreCase = true)
        val headline = "HTTP $code (${httpInvalid.responseMessage ?: error.errorCodeName})"
        val hint = when (code) {
            403, 451 -> if (isJioCdn) {
                "Stream blocked (HTTP $code). JioTV streams require an Indian network IP (cloud emulators run outside India) or a fresh playlist token."
            } else {
                "Access denied (HTTP $code). The stream may be geo-restricted or its auth token expired."
            }
            401 -> "Unauthorized (HTTP 401). The channel token may have expired — tap Refresh Token."
            404, 410 -> "Stream offline or moved (HTTP $code). Try refreshing the playlist."
            else -> "Server returned HTTP $code. Try refreshing the playlist or retrying."
        }
        return headline to hint
    }

    val isHttpNetwork = generateSequence<Throwable>(error) { it.cause }
        .any { it is HttpDataSource.HttpDataSourceException }
    if (isHttpNetwork) {
        return "Network Error (${error.errorCodeName})" to
            (rootCause.message ?: "Unable to connect to stream server. Check internet connection.")
    }

    return "Playback Error (${error.errorCodeName})" to
        (rootCause.message ?: error.message ?: "Stream unavailable")
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: TvChannel,
    isFavorite: Boolean,
    isRefreshingChannel: Boolean,
    relatedChannels: List<TvChannel>,
    onSelectChannel: (TvChannel) -> Unit,
    onToggleFavorite: () -> Unit,
    onRefreshChannel: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLandscape by remember { mutableStateOf(false) }
    var errorHeadline by remember { mutableStateOf<String?>(null) }
    var errorHint by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    // Mode 0: Enhanced headers + segment __hdnea__ propagation.
    // Mode 1: Strict raw playlist URL/headers fallback (auto-triggered on HTTP 400/403).
    var compatMode by remember(channel.id, channel.streamUrl) { mutableIntStateOf(0) }

    val hdneaToken = remember(channel) {
        val cookie = channel.headers.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            .orEmpty()
        val fromCookie = Regex("__hdnea__=[^;\\s&]+").find(cookie)?.value
        val fromUrl = Regex("__hdnea__=[^&\\s]+").find(channel.streamUrl)?.value
        fromCookie ?: fromUrl
    }

    val requestHeaders = remember(channel, compatMode, hdneaToken) {
        val map = LinkedHashMap<String, String>()
        channel.headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true)) {
                map[k] = v
            }
        }
        // Ensure Cookie header carries __hdnea__ when token was only present in the URL query
        if (!hdneaToken.isNullOrBlank() &&
            map.keys.none { it.equals("Cookie", ignoreCase = true) }
        ) {
            map["Cookie"] = hdneaToken
        }
        if (compatMode == 0 && channel.streamUrl.contains(".cdn.jio.com", ignoreCase = true)) {
            if (map.keys.none { it.equals("Referer", ignoreCase = true) }) {
                map["Referer"] = "https://www.jiotv.com/"
            }
            if (map.keys.none { it.equals("Origin", ignoreCase = true) }) {
                map["Origin"] = "https://www.jiotv.com"
            }
        }
        map
    }

    val userAgent = remember(channel) {
        channel.headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: "plaYtv/7.1.5 (Linux;Android 13) ExoPlayerLib/2.11.7"
    }

    val finalStreamUrl = remember(channel, compatMode, hdneaToken) {
        if (compatMode == 0 &&
            !hdneaToken.isNullOrBlank() &&
            !channel.streamUrl.contains("__hdnea__=")
        ) {
            val sep = if (channel.streamUrl.contains("?")) "&" else "?"
            channel.streamUrl + sep + hdneaToken
        } else {
            channel.streamUrl
        }
    }

    val exoPlayer = remember(channel.id, channel.streamUrl, channel.headers, compatMode, reloadKey) {
        errorHeadline = null
        errorHint = null

        val rawHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(25_000)
            .setDefaultRequestProperties(requestHeaders)

        val dataSourceFactory: DataSource.Factory = if (compatMode == 0 && !hdneaToken.isNullOrBlank()) {
            DataSource.Factory { AuthTokenDataSource(rawHttpFactory.createDataSource(), hdneaToken) }
        } else {
            rawHttpFactory
        }

        val drmSessionManager = when {
            channel.clearKeys.isNotEmpty() -> {
                val jwkBytes = M3uParser.buildClearKeyJwkBytes(channel.clearKeys)
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        C.CLEARKEY_UUID,
                        FrameworkMediaDrm.DEFAULT_PROVIDER
                    )
                    .setMultiSession(true)
                    .build(LocalMediaDrmCallback(jwkBytes))
            }

            channel.licenseUrl.isNotBlank() && channel.licenseType.contains("widevine") -> {
                val callback = HttpMediaDrmCallback(channel.licenseUrl, rawHttpFactory)
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        C.WIDEVINE_UUID,
                        FrameworkMediaDrm.DEFAULT_PROVIDER
                    )
                    .setMultiSession(true)
                    .build(callback)
            }

            channel.licenseUrl.isNotBlank() && channel.licenseType.contains("clearkey") -> {
                val callback = HttpMediaDrmCallback(channel.licenseUrl, rawHttpFactory)
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        C.CLEARKEY_UUID,
                        FrameworkMediaDrm.DEFAULT_PROVIDER
                    )
                    .setMultiSession(true)
                    .build(callback)
            }

            else -> null
        }

        val mediaItem = MediaItem.Builder()
            .setUri(finalStreamUrl)
            .apply {
                if (channel.isDash) {
                    setMimeType(MimeTypes.APPLICATION_MPD)
                } else if (finalStreamUrl.contains(".m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        val mediaSource = when {
            channel.isDash -> DashMediaSource.Factory(dataSourceFactory)
                .apply { drmSessionManager?.let { manager -> setDrmSessionManagerProvider { manager } } }
                .createMediaSource(mediaItem)

            finalStreamUrl.contains(".m3u8", ignoreCase = true) -> HlsMediaSource.Factory(dataSourceFactory)
                .apply { drmSessionManager?.let { manager -> setDrmSessionManagerProvider { manager } } }
                .createMediaSource(mediaItem)

            else -> DefaultMediaSourceFactory(dataSourceFactory)
                .apply { drmSessionManager?.let { manager -> setDrmSessionManagerProvider { manager } } }
                .createMediaSource(mediaItem)
        }

        // Enable software decoder fallback so streams also decode reliably on emulators & diverse devices
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs
                50_000, // maxBufferMs
                1_500,  // bufferForPlaybackMs
                3_000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        val trackSelector = DefaultTrackSelector(context)
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaSource(mediaSource)
                playWhenReady = true
                prepare()
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val httpCode = generateSequence<Throwable>(error) { it.cause }
                            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                            .firstOrNull()
                            ?.responseCode

                        // Automatically try strict raw playlist mode once on HTTP 400/403
                        if (compatMode == 0 && (httpCode == 400 || httpCode == 403)) {
                            compatMode = 1
                            return
                        }

                        val (headline, hint) = buildReadableErrorMessage(error, finalStreamUrl)
                        errorHeadline = headline
                        errorHint = hint
                    }
                })
            }
    }

    // Keep screen awake during live TV streaming
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Pause when backgrounded (unless in Picture-in-Picture) and resume on return
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (errorHeadline == null) {
                        exoPlayer.playWhenReady = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Manage fullscreen system bars & orientation
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

    // Release player and restore orientation/system bars when leaving player
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

    val resizeLabel = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> stringResource(R.string.aspect_zoom)
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> stringResource(R.string.aspect_fill)
        else -> stringResource(R.string.aspect_fit)
    }

    val isInPipMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        activity?.isInPictureInPictureMode == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Player Bar (hidden in landscape fullscreen or PiP)
        if (!isLandscape && !isInPipMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(channel.group)
                            append(" · ")
                            append(if (channel.isDash) "DASH" else "HLS")
                            if (channel.clearKeys.isNotEmpty() || channel.licenseType.contains("clearkey")) {
                                append(" · ClearKey")
                            } else if (channel.licenseType.contains("widevine")) {
                                append(" · Widevine")
                            }
                            append(" · ")
                            append(resizeLabel)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                    IconButton(
                        onClick = {
                            runCatching {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                                activity.enterPictureInPictureMode(params)
                            }
                        },
                        modifier = Modifier.testTag("player_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = stringResource(R.string.cd_pip),
                            tint = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.testTag("player_aspect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = stringResource(R.string.cd_aspect_ratio),
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { isLandscape = !isLandscape },
                    modifier = Modifier.testTag("player_rotate_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = stringResource(R.string.cd_rotate),
                        tint = Color.White
                    )
                }
            }
        }

        // Video Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLandscape || isInPipMode) Modifier.fillMaxSize()
                    else Modifier.aspectRatio(16f / 9f)
                )
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("exoplayer_view"),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = !isInPipMode
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
                    view.useController = !isInPipMode
                }
            )

            // Exit-fullscreen button in landscape top-right
            if (isLandscape && !isInPipMode) {
                IconButton(
                    onClick = { isLandscape = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .testTag("player_exit_landscape_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = stringResource(R.string.cd_rotate),
                        tint = Color.White
                    )
                }
            }

            if (isRefreshingChannel) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xEE11131A))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CyanGlow, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.refreshing_stream_token),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                errorHeadline?.let { headline ->
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xEE11131A))
                            .border(1.dp, DarkOutline, RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = headline,
                            color = ErrorSoft,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        errorHint?.let { hint ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = hint,
                                color = DarkOnSurfaceMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    compatMode = 0
                                    onRefreshChannel()
                                    reloadKey++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanGlow,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("refresh_token_retry_button")
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_refresh_token),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    compatMode = 0
                                    reloadKey++
                                },
                                modifier = Modifier.testTag("retry_stream_button")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.action_retry),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Portrait Channel Info & Quick Channel Switcher
        if (!isLandscape && !isInPipMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PlusTvBrandGradient)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.live_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.now_playing_label) + " · " + channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            compatMode = 0
                            onRefreshChannel()
                            reloadKey++
                        },
                        modifier = Modifier.testTag("player_reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_reload_stream),
                            tint = DarkOnSurfaceMuted
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("player_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                            tint = if (isFavorite) StarGold else DarkOnSurfaceMuted
                        )
                    }
                }
            }

            HorizontalDivider(color = DarkOutline, thickness = 1.dp)

            if (relatedChannels.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(PlusTvBrandGradient)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.up_next_in_category, channel.group),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }

                    items(relatedChannels, key = { it.id }) { item ->
                        val isCurrent = item.id == channel.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrent) Color(0xFF1A2233) else DarkSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) CyanGlow else DarkOutline,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectChannel(item) }
                                .padding(10.dp)
                                .testTag("related_channel_${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp, 40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkTileStage)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.logo.isNotBlank()) {
                                    AsyncImage(
                                        model = item.logo,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = item.name.take(2).uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CyanGlow
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.group} · ${if (item.isDash) "DASH" else "HLS"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DarkOnSurfaceMuted,
                                    maxLines = 1
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isCurrent) CyanGlow else DarkOnSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
