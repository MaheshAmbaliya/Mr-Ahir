package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.TvChannel
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkOnSurfaceMuted
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTileStage
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PlusTvBrandGradient
import com.example.ui.theme.VividFuchsia

data class VideoTrackOption(
    val id: String,
    val label: String,
    val detail: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

data class AudioTrackOption(
    val id: String,
    val label: String,
    val detail: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

data class StreamTelemetry(
    val audioCodec: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
) {
    val shortResolution: String
        get() = if (videoHeight > 0) "${videoHeight}p" else ""
}

private val PlayPauseCircleGradient = Brush.linearGradient(
    colors = listOf(CyanGlow, ElectricBlue, VividFuchsia, NeonPink)
)

private val VerticalAccentGradient = Brush.verticalGradient(
    colors = listOf(CyanGlow, VividFuchsia)
)

private val LivePillGradient = Brush.horizontalGradient(
    colors = listOf(CyanGlow, VividFuchsia, NeonPink)
)

@Composable
fun PlayerVideoOverlay(
    channelName: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isMuted: Boolean,
    isFavorite: Boolean,
    isLandscape: Boolean,
    showControls: Boolean,
    canNavigatePrev: Boolean,
    canNavigateNext: Boolean,
    ratioToastText: String?,
    telemetry: StreamTelemetry,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenTracks: () -> Unit,
    onPrevChannel: () -> Unit,
    onPlayPause: () -> Unit,
    onNextChannel: () -> Unit,
    onGoLive: () -> Unit,
    onToggleMute: () -> Unit,
    onCycleRatio: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val horizontalBarPadding = if (isLandscape) 16.dp else 8.dp
    val landscapeCutoutMod = if (isLandscape) Modifier.displayCutoutPadding() else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            )
            .testTag("player_overlay_touch_layer")
    ) {
        // Controls Overlay (Top, Center, Bottom)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x4D000000))
            ) {
                // Top Scrim + Top Bar (Left group takes available space; right icons stay pinned to far right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xCC000000), Color.Transparent)
                            )
                        )
                        .then(landscapeCutoutMod)
                        .padding(
                            horizontal = horizontalBarPadding,
                            vertical = if (isLandscape) 10.dp else 6.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left cluster: Back + Channel Name + LIVE pill
                    Row(
                        modifier = Modifier.weight(1f),
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

                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(LivePillGradient)
                                .padding(horizontal = 9.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.live_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Right cluster pinned cleanly to the right edge: Heart + Tracks Settings
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag("player_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.cd_toggle_favorite),
                                tint = if (isFavorite) NeonPink else Color.White
                            )
                        }

                        IconButton(
                            onClick = onOpenTracks,
                            modifier = Modifier.testTag("player_tracks_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.cd_tracks_settings),
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Transport Controls (Prev / Play-Pause / Next)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(
                        onClick = onPrevChannel,
                        enabled = canNavigatePrev,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x55000000))
                            .testTag("player_prev_channel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.cd_previous_channel),
                            tint = if (canNavigatePrev) Color.White else Color(0x66FFFFFF),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (isBuffering) {
                        Box(modifier = Modifier.size(64.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PlayPauseCircleGradient)
                                .clickable(onClick = onPlayPause)
                                .testTag("player_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) {
                                    stringResource(R.string.cd_pause)
                                } else {
                                    stringResource(R.string.cd_play)
                                },
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNextChannel,
                        enabled = canNavigateNext,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x55000000))
                            .testTag("player_next_channel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.cd_next_channel),
                            tint = if (canNavigateNext) Color.White else Color(0x66FFFFFF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Scrim + Bottom Bar (GO LIVE + 1080p AAC on left; Mute + AspectRatio + Fullscreen on far right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        .then(landscapeCutoutMod)
                        .padding(
                            horizontal = horizontalBarPadding,
                            vertical = if (isLandscape) 10.dp else 6.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xAA161722))
                                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(999.dp))
                                .clickable(onClick = onGoLive)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("player_go_live_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NeonPink)
                            )
                            Text(
                                text = stringResource(R.string.go_live),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        if (telemetry.shortResolution.isNotEmpty()) {
                            Text(
                                text = telemetry.shortResolution,
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanGlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        if (telemetry.audioCodec.isNotEmpty()) {
                            Text(
                                text = telemetry.audioCodec,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xDDFFFFFF),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier.testTag("player_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isMuted) {
                                    stringResource(R.string.cd_unmute)
                                } else {
                                    stringResource(R.string.cd_mute)
                                },
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onCycleRatio,
                            modifier = Modifier.testTag("player_aspect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = stringResource(R.string.cd_aspect_ratio),
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.testTag("player_rotate_button")
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isLandscape) {
                                    stringResource(R.string.cd_exit_fullscreen)
                                } else {
                                    stringResource(R.string.cd_fullscreen)
                                },
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Brief Aspect Ratio Feedback Pill (e.g., "Ratio: Zoom")
        AnimatedVisibility(
            visible = ratioToastText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 56.dp else 44.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xCC0D0E16))
                    .border(1.dp, CyanGlow.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = ratioToastText.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Always-Visible Buffering Overlay (even when controls are hidden)
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("player_buffering_indicator"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA0C0D14))
                            .border(1.5.dp, PlusTvBrandGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CyanGlow,
                            trackColor = Color(0x33FFFFFF),
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xBB0C0D14))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.buffering_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                LinearProgressIndicator(
                    color = CyanGlow,
                    trackColor = Color(0x33FFFFFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun MoreInCategoryHeader(category: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(VerticalAccentGradient)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.up_next_in_category, category),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RelatedChannelGridTile(
    channel: TvChannel,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF12131B))
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) CyanGlow else DarkOutline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("related_channel_${channel.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
                .background(DarkTileStage)
        ) {
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 18.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
                        .fillMaxWidth(0.76f)
                        .fillMaxHeight(0.74f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 14.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PlusTvBrandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(LivePillGradient)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.live_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 9.sp
                )
            }
        }

        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) CyanGlow else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksSelectionBottomSheet(
    videoOptions: List<VideoTrackOption>,
    audioOptions: List<AudioTrackOption>,
    isAutoVideo: Boolean,
    isAutoAudio: Boolean,
    onSelectAutoVideo: () -> Unit,
    onSelectVideoTrack: (VideoTrackOption) -> Unit,
    onSelectAutoAudio: () -> Unit,
    onSelectAudioTrack: (AudioTrackOption) -> Unit,
    onRefreshToken: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tracks_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                TextButton(
                    onClick = {
                        onRefreshToken()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sheet_refresh_token_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_refresh_token),
                        color = CyanGlow
                    )
                }
            }

            HorizontalDivider(color = DarkOutline)

            Text(
                text = stringResource(R.string.video_quality_section),
                style = MaterialTheme.typography.titleMedium,
                color = CyanGlow
            )

            TrackChoiceRow(
                title = stringResource(R.string.auto_quality),
                subtitle = null,
                selected = isAutoVideo,
                onClick = {
                    onSelectAutoVideo()
                    onDismiss()
                }
            )

            videoOptions.forEach { option ->
                TrackChoiceRow(
                    title = option.label,
                    subtitle = option.detail.ifBlank { null },
                    selected = !isAutoVideo && option.isSelected,
                    onClick = {
                        onSelectVideoTrack(option)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = DarkOutline)

            Text(
                text = stringResource(R.string.audio_tracks_section),
                style = MaterialTheme.typography.titleMedium,
                color = CyanGlow
            )

            TrackChoiceRow(
                title = stringResource(R.string.auto_audio),
                subtitle = null,
                selected = isAutoAudio,
                onClick = {
                    onSelectAutoAudio()
                    onDismiss()
                }
            )

            audioOptions.forEach { option ->
                TrackChoiceRow(
                    title = option.label,
                    subtitle = option.detail.ifBlank { null },
                    selected = !isAutoAudio && option.isSelected,
                    onClick = {
                        onSelectAudioTrack(option)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF182436) else Color(0xFF0E0F16))
            .border(
                width = 1.dp,
                color = if (selected) CyanGlow else DarkOutline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) CyanGlow else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceMuted,
                    fontSize = 12.sp
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = CyanGlow
            )
        }
    }
}
