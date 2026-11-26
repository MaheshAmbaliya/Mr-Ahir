package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
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
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.M3uParser
import com.example.data.TvChannel
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOnSurfaceMuted
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.ErrorSoft
import kotlinx.coroutines.delay
import java.util.Locale

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
                "Stream blocked (HTTP $code). JioTV streams require an Indian network IP or a fresh playlist token."
            } else {
                "Access denied (HTTP $code). The stream may be geo-restricted or its auth token expired."
            }
            401 -> "Unauthorized (HTTP 401). Tap Refresh Token to fetch a fresh stream key."
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

private fun formatAudioCodec(mime: String?): String = when {
    mime == null -> ""
    mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
    mime.contains("eac3", ignoreCase = true) -> "EAC3"
    mime.contains("ac3", ignoreCase = true) -> "AC3"
    mime.contains("opus", ignoreCase = true) -> "Opus"
    mime.contains("mpeg", ignoreCase = true) -> "MP3"
    else -> mime.substringAfter('/').uppercase(Locale.US)
}

private fun formatChannelLayout(channelCount: Int): String = when (channelCount) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    in 3..Int.MAX_VALUE -> "${channelCount}ch"
    else -> ""
}

private fun formatSampleRate(sampleRate: Int): String {
    if (sampleRate <= 0) return ""
    val khz = sampleRate / 1000.0
    return if (sampleRate % 1000 == 0) {
        "${sampleRate / 1000}kHz"
    } else {
        String.format(Locale.US, "%.1fkHz", khz)
    }
}

private fun extractTrackLists(tracks: Tracks): Pair<List<VideoTrackOption>, List<AudioTrackOption>> {
    val videos = mutableListOf<VideoTrackOption>()
    val audios = mutableListOf<AudioTrackOption>()

    tracks.groups.forEachIndexed { groupIdx, group ->
        when (group.type) {
            C.TRACK_TYPE_VIDEO -> {
                for (trackIdx in 0 until group.length) {
                    if (!group.isTrackSupported(trackIdx)) continue
                    val fmt = group.getTrackFormat(trackIdx)
                    val label = if (fmt.height > 0) "${fmt.height}p" else "Video ${videos.size + 1}"
                    val detailParts = buildList {
                        if (fmt.width > 0 && fmt.height > 0) add("${fmt.width}×${fmt.height}")
                        if (fmt.bitrate > 0) {
                            add(String.format(Locale.US, "%.1f Mbps", fmt.bitrate / 1_000_000f))
                        }
                        if (fmt.frameRate > 0f) add("${fmt.frameRate.toInt()} fps")
                    }
                    videos.add(
                        VideoTrackOption(
                            id = "v-$groupIdx-$trackIdx",
                            label = label,
                            detail = detailParts.joinToString(" • "),
                            groupIndex = groupIdx,
                            trackIndex = trackIdx,
                            isSelected = group.isTrackSelected(trackIdx)
                        )
                    )
                }
            }

            C.TRACK_TYPE_AUDIO -> {
                for (trackIdx in 0 until group.length) {
                    if (!group.isTrackSupported(trackIdx)) continue
                    val fmt = group.getTrackFormat(trackIdx)
                    val lang = fmt.language?.takeIf { it.isNotBlank() && it != "und" }
                        ?.let { Locale(it).displayLanguage.replaceFirstChar(Char::uppercase) }
                    val label = fmt.label?.takeIf { it.isNotBlank() }
                        ?: lang
                        ?: "Audio ${audios.size + 1}"
                    val detailParts = buildList {
                        val codec = formatAudioCodec(fmt.sampleMimeType)
                        if (codec.isNotEmpty()) add(codec)
                        val layout = formatChannelLayout(fmt.channelCount)
                        if (layout.isNotEmpty()) add(layout)
                        val sr = formatSampleRate(fmt.sampleRate)
                        if (sr.isNotEmpty()) add(sr)
                    }
                    audios.add(
                        AudioTrackOption(
                            id = "a-$groupIdx-$trackIdx",
                            label = label,
                            detail = detailParts.joinToString(" • "),
                            groupIndex = groupIdx,
                            trackIndex = trackIdx,
                            isSelected = group.isTrackSelected(trackIdx)
                        )
                    )
                }
            }
        }
    }

    val sortedVideos = videos.sortedByDescending { opt ->
        opt.label.removeSuffix("p").toIntOrNull() ?: 0
    }
    return sortedVideos to audios
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
    var ratioToastText by remember { mutableStateOf<String?>(null) }
    var ratioToastTick by remember { mutableIntStateOf(0) }
    var isLandscape by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember(channel.id) { mutableStateOf(true) }
    var showBufferingUi by remember(channel.id) { mutableStateOf(true) }
    var showControls by remember(channel.id) { mutableStateOf(true) }
    var controlsTouchTick by remember { mutableIntStateOf(0) }
    var showTracksSheet by remember { mutableStateOf(false) }

    // 0 = Playlist headers + NS Player UA (clean URL), 1 = JioTV UA + token, 2 = Pure Lavf/ExoPlayer UA without extra headers
    var compatMode by remember(channel.id, channel.streamUrl) { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var errorHeadline by remember { mutableStateOf<String?>(null) }
    var errorHint by remember { mutableStateOf<String?>(null) }

    var telemetry by remember(channel.id) { mutableStateOf(StreamTelemetry()) }
    var videoOptions by remember(channel.id) { mutableStateOf<List<VideoTrackOption>>(emptyList()) }
    var audioOptions by remember(channel.id) { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var isAutoVideo by remember(channel.id) { mutableStateOf(true) }
    var isAutoAudio by remember(channel.id) { mutableStateOf(true) }

    LaunchedEffect(isBuffering, isRefreshingChannel) {
        if (isRefreshingChannel) {
            showBufferingUi = true
        } else if (isBuffering) {
            delay(250)
            showBufferingUi = true
        } else {
            showBufferingUi = false
        }
    }

    LaunchedEffect(showControls, isPlaying, isBuffering, controlsTouchTick) {
        if (showControls && isPlaying && !isBuffering && errorHeadline == null) {
            delay(4_000)
            showControls = false
        }
    }

    LaunchedEffect(ratioToastTick) {
        if (ratioToastText != null) {
            delay(1_400)
            ratioToastText = null
        }
    }

    val currentIndex = remember(relatedChannels, channel.id) {
        relatedChannels.indexOfFirst { it.id == channel.id }
    }
    val canNavigatePrev = currentIndex > 0
    val canNavigateNext = currentIndex in 0 until (relatedChannels.size - 1)

    val hdneaToken = remember(channel) {
        val cookie = channel.headers.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            .orEmpty()
        val fromCookie = Regex("__hdnea__=[^;\\s&]+").find(cookie)?.value
        val fromUrl = Regex("__hdnea__=[^&\\s]+").find(channel.streamUrl)?.value
        fromCookie ?: fromUrl
    }

    val userAgent = remember(channel, compatMode) {
        val playlistUa = channel.headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }

        when (compatMode) {
            0 -> playlistUa ?: "NS Player/1.0 (Linux; Android 13) ExoPlayerLib/2.18.1"
            1 -> playlistUa ?: "plaYtv/7.1.5 (Linux;Android 13) ExoPlayerLib/2.11.7"
            else -> "Lavf/58.76.100"
        }
    }

    val requestHeaders = remember(channel, compatMode, hdneaToken, userAgent) {
        val map = LinkedHashMap<String, String>()
        map["User-Agent"] = userAgent

        if (compatMode != 2) {
            channel.headers.forEach { (k, v) ->
                if (v.isNotBlank() && !k.equals("User-Agent", ignoreCase = true)) {
                    map[k] = v
                }
            }
            if (!hdneaToken.isNullOrBlank() &&
                map.keys.none { it.equals("Cookie", ignoreCase = true) }
            ) {
                map["Cookie"] = hdneaToken
            }
            if (compatMode == 1 && channel.streamUrl.contains(".cdn.jio.com", ignoreCase = true)) {
                if (map.keys.none { it.equals("Referer", ignoreCase = true) }) {
                    map["Referer"] = "https://www.jiotv.com/"
                }
                if (map.keys.none { it.equals("Origin", ignoreCase = true) }) {
                    map["Origin"] = "https://www.jiotv.com"
                }
            }
        }
        map
    }

    val finalStreamUrl = remember(channel, compatMode, hdneaToken) {
        if (compatMode == 1 &&
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
        isBuffering = true

        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(1_200_000L)
            .setResetOnNetworkTypeChange(true)
            .build()

        val rawHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
            .setTransferListener(bandwidthMeter)
            .setDefaultRequestProperties(requestHeaders)

        val dataSourceFactory: DataSource.Factory = if (compatMode == 1 && !hdneaToken.isNullOrBlank()) {
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

        val loadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(2)

        val mediaSource = when {
            channel.isDash -> DashMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                .apply { drmSessionManager?.let { manager -> setDrmSessionManagerProvider { manager } } }
                .createMediaSource(mediaItem)

            finalStreamUrl.contains(".m3u8", ignoreCase = true) -> HlsMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                .apply { drmSessionManager?.let { manager -> setDrmSessionManagerProvider { manager } } }
                .createMediaSource(mediaItem)

            else -> DefaultMediaSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                .apply { drmSessionManager?.let { manager -> setDrmSessionManagerProvider { manager } } }
                .createMediaSource(mediaItem)
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                6_000,
                25_000,
                600,
                1_500
            )
            .build()

        val adaptiveTrackFactory = AdaptiveTrackSelection.Factory(
            2_500,
            3_000,
            4_000,
            0.75f
        )
        val trackSelector = DefaultTrackSelector(context, adaptiveTrackFactory).apply {
            setParameters(
                buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .build()
            .apply {
                volume = if (isMuted) 0f else 1f
                setMediaSource(mediaSource)
                playWhenReady = true
                prepare()

                fun syncFormats(audioFmt: Format?, videoFmt: Format?) {
                    telemetry = telemetry.copy(
                        audioCodec = formatAudioCodec(audioFmt?.sampleMimeType).ifEmpty { telemetry.audioCodec },
                        videoWidth = videoFmt?.width?.takeIf { it > 0 } ?: telemetry.videoWidth,
                        videoHeight = videoFmt?.height?.takeIf { it > 0 } ?: telemetry.videoHeight
                    )
                }

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING ||
                            (playbackState == Player.STATE_IDLE && errorHeadline == null)
                        if (playbackState == Player.STATE_READY) {
                            syncFormats(audioFormat, videoFormat)
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            telemetry = telemetry.copy(
                                videoWidth = videoSize.width,
                                videoHeight = videoSize.height
                            )
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val (videos, audios) = extractTrackLists(tracks)
                        videoOptions = videos
                        audioOptions = audios
                        syncFormats(audioFormat, videoFormat)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        val httpCode = generateSequence<Throwable>(error) { it.cause }
                            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                            .firstOrNull()
                            ?.responseCode

                        if (compatMode < 2 && (httpCode == 400 || httpCode == 403 || httpCode == null)) {
                            compatMode += 1
                            return
                        }

                        val (headline, hint) = buildReadableErrorMessage(error, finalStreamUrl)
                        errorHeadline = headline
                        errorHint = hint
                    }
                })
            }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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

    DisposableEffect(isLandscape) {
        resizeMode = if (isLandscape) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        activity?.let { act ->
            act.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                act.window.attributes = act.window.attributes.apply {
                    layoutInDisplayCutoutMode = if (isLandscape) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    act.window.attributes = act.window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val fitLabel = stringResource(R.string.aspect_fit)
    val zoomLabel = stringResource(R.string.aspect_zoom)
    val fillLabel = stringResource(R.string.aspect_fill)

    val isInPipMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        activity?.isInPictureInPictureMode == true

    val cycleRatio: () -> Unit = {
        val (nextMode, nextLabel) = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM to zoomLabel
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL to fillLabel
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT to fitLabel
        }
        resizeMode = nextMode
        ratioToastText = context.getString(R.string.ratio_pill_format, nextLabel)
        ratioToastTick++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .then(if (!isLandscape && !isInPipMode) Modifier.statusBarsPadding() else Modifier)
    ) {
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
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = resizeMode
                    view.useController = false
                }
            )

            if (!isInPipMode) {
                PlayerVideoOverlay(
                    channelName = channel.name,
                    isPlaying = isPlaying,
                    isBuffering = showBufferingUi && errorHeadline == null,
                    isMuted = isMuted,
                    isFavorite = isFavorite,
                    isLandscape = isLandscape,
                    showControls = showControls && errorHeadline == null,
                    canNavigatePrev = canNavigatePrev,
                    canNavigateNext = canNavigateNext,
                    ratioToastText = ratioToastText,
                    telemetry = telemetry,
                    onToggleControls = {
                        showControls = !showControls
                        controlsTouchTick++
                    },
                    onBack = {
                        if (isLandscape) isLandscape = false else onBack()
                    },
                    onToggleFavorite = {
                        controlsTouchTick++
                        onToggleFavorite()
                    },
                    onOpenTracks = {
                        controlsTouchTick++
                        showTracksSheet = true
                    },
                    onPrevChannel = {
                        if (canNavigatePrev) {
                            onSelectChannel(relatedChannels[currentIndex - 1])
                        }
                    },
                    onPlayPause = {
                        controlsTouchTick++
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onNextChannel = {
                        if (canNavigateNext) {
                            onSelectChannel(relatedChannels[currentIndex + 1])
                        }
                    },
                    onGoLive = {
                        controlsTouchTick++
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    },
                    onToggleMute = {
                        controlsTouchTick++
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    onCycleRatio = {
                        controlsTouchTick++
                        cycleRatio()
                    },
                    onToggleFullscreen = {
                        controlsTouchTick++
                        isLandscape = !isLandscape
                    }
                )
            }

            errorHeadline?.let { headline ->
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xEE11131A))
                        .border(1.dp, DarkOutline, RoundedCornerShape(16.dp))
                        .padding(16.dp),
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
                    Spacer(Modifier.height(12.dp))
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

        if (!isLandscape && !isInPipMode && relatedChannels.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MoreInCategoryHeader(category = channel.group)
                }

                items(relatedChannels, key = { it.id }) { item ->
                    RelatedChannelGridTile(
                        channel = item,
                        isCurrent = item.id == channel.id,
                        onClick = { onSelectChannel(item) }
                    )
                }
            }
        }
    }

    if (showTracksSheet) {
        TracksSelectionBottomSheet(
            videoOptions = videoOptions,
            audioOptions = audioOptions,
            isAutoVideo = isAutoVideo,
            isAutoAudio = isAutoAudio,
            onSelectAutoVideo = {
                isAutoVideo = true
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
            },
            onSelectVideoTrack = { option ->
                val group = exoPlayer.currentTracks.groups.getOrNull(option.groupIndex)
                if (group != null) {
                    isAutoVideo = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                        )
                        .build()
                }
            },
            onSelectAutoAudio = {
                isAutoAudio = true
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .build()
            },
            onSelectAudioTrack = { option ->
                val group = exoPlayer.currentTracks.groups.getOrNull(option.groupIndex)
                if (group != null) {
                    isAutoAudio = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                        )
                        .build()
                }
            },
            onRefreshToken = {
                compatMode = 0
                onRefreshChannel()
                reloadKey++
            },
            onDismiss = { showTracksSheet = false }
        )
    }
}
