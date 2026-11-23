package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.DarkTileStage
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.LightTileStage
import com.example.ui.theme.PlusTvBrandGradient
import com.example.ui.theme.StarGold

@Composable
fun PlusTvHomeScreen(
    uiState: PlusTvUiState,
    onLoadPlaylist: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSearch: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenChannel: (TvChannel) -> Unit
) {
    var showUrlDialog by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val categories = remember(uiState.channels) { uiState.categories }
    val filteredChannels = remember(
        uiState.channels,
        uiState.selectedCategory,
        uiState.searchQuery,
        uiState.showFavoritesOnly,
        uiState.favorites
    ) { uiState.filteredChannels }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // MiniTV Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surface)
                        .border(1.dp, colorScheme.outline, CircleShape)
                        .clickable { showUrlDialog = true }
                        .testTag("playlist_settings_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.cd_playlist_settings),
                        tint = colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.brand_wordmark),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        brush = PlusTvBrandGradient
                    ),
                    modifier = Modifier.testTag("brand_wordmark")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surface)
                        .border(1.dp, colorScheme.outline, CircleShape)
                        .clickable(onClick = onToggleTheme)
                        .testTag("theme_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = stringResource(R.string.cd_toggle_theme),
                        tint = colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (uiState.searchOpen) CyanGlow else colorScheme.surface)
                        .border(1.dp, colorScheme.outline, CircleShape)
                        .clickable(onClick = onToggleSearch)
                        .testTag("search_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_toggle_search),
                        tint = if (uiState.searchOpen) Color.Black else colorScheme.onSurface
                    )
                }
            }
        }

        HorizontalDivider(color = colorScheme.outline, thickness = 1.dp)

        // Expandable Search Input
        AnimatedVisibility(visible = uiState.searchOpen) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onUpdateSearchQuery,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        color = colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onUpdateSearchQuery("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("search_input"),
                shape = RoundedCornerShape(999.dp)
            )
        }

        // Subheader: Live Streaming Now + Gradient Channel Count Pill (toggles Favorites)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (uiState.showFavoritesOnly) {
                    stringResource(R.string.favorite_channels)
                } else {
                    stringResource(R.string.live_streaming_now)
                },
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PlusTvBrandGradient)
                    .clickable(onClick = onToggleFavoritesFilter)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("channel_count_favorites_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (uiState.showFavoritesOnly) Icons.Default.Star else Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.channels_count_format, filteredChannels.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Horizontal Category Tabs
        if (categories.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.take(20).forEach { cat ->
                    val active = uiState.selectedCategory == cat && !uiState.showFavoritesOnly
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .then(
                                if (active) {
                                    Modifier.background(PlusTvBrandGradient)
                                } else {
                                    Modifier
                                        .background(colorScheme.surface)
                                        .border(1.dp, colorScheme.outline, RoundedCornerShape(999.dp))
                                }
                            )
                            .clickable { onSelectCategory(cat) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .testTag("category_chip_$cat")
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) Color.White else colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Main Adaptive Channel Grid / Loading / Error / Empty States
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyanGlow)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.loading_channels),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.errorText != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = ErrorCoral,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = uiState.errorText,
                            color = ErrorCoral,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onLoadPlaylist(uiState.playlistUrl) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanGlow,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("retry_playlist_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_retry))
                            }
                            OutlinedButton(
                                onClick = { showUrlDialog = true },
                                modifier = Modifier.testTag("change_url_error_button")
                            ) {
                                Text(stringResource(R.string.playlist_url_dialog_title))
                            }
                        }
                    }
                }
            }

            filteredChannels.isEmpty() -> {
                val title = when {
                    uiState.showFavoritesOnly -> stringResource(R.string.empty_favorites_title)
                    uiState.searchQuery.isNotBlank() || uiState.selectedCategory != "All" ->
                        stringResource(R.string.empty_search_title)
                    else -> stringResource(R.string.empty_playlist_title)
                }
                val subtitle = when {
                    uiState.showFavoritesOnly -> stringResource(R.string.empty_favorites_subtitle)
                    uiState.searchQuery.isNotBlank() || uiState.selectedCategory != "All" ->
                        stringResource(R.string.empty_search_subtitle)
                    else -> stringResource(R.string.empty_playlist_subtitle)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.showFavoritesOnly) Icons.Default.StarBorder else Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        if (uiState.channels.isNotEmpty()) {
                            Button(
                                onClick = onClearFilters,
                                modifier = Modifier.testTag("clear_filters_button")
                            ) {
                                Text(stringResource(R.string.action_clear_filters))
                            }
                        } else {
                            Button(
                                onClick = { onLoadPlaylist(uiState.playlistUrl) },
                                modifier = Modifier.testTag("retry_empty_playlist_button")
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("channels_grid")
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(PlusTvBrandGradient)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (uiState.showFavoritesOnly) {
                                    stringResource(R.string.favorites_title)
                                } else {
                                    uiState.selectedCategory
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    items(filteredChannels, key = { it.id }) { channel ->
                        val isFav = uiState.favorites.contains(channel.id)
                        PlusTvTileCard(
                            channel = channel,
                            isFav = isFav,
                            isDark = uiState.isDark,
                            onClick = { onOpenChannel(channel) },
                            onToggleFav = { onToggleFavorite(channel.id) }
                        )
                    }
                }
            }
        }
    }

    // M3U Playlist URL Settings Dialog
    if (showUrlDialog) {
        var draft by remember(uiState.playlistUrl) { mutableStateOf(uiState.playlistUrl) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.playlist_url_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.playlist_url_helper),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.playlist_url_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_url_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { draft = DEFAULT_M3U_URL },
                            modifier = Modifier.testTag("reset_default_url_button")
                        ) {
                            Text(stringResource(R.string.action_reset_default))
                        }
                        TextButton(
                            onClick = {
                                onLoadPlaylist(uiState.playlistUrl)
                                showUrlDialog = false
                            },
                            modifier = Modifier.testTag("refresh_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_refresh))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            onLoadPlaylist(trimmed)
                        }
                        showUrlDialog = false
                    },
                    modifier = Modifier.testTag("load_playlist_button")
                ) {
                    Text(stringResource(R.string.action_load))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUrlDialog = false },
                    modifier = Modifier.testTag("cancel_playlist_dialog_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PlusTvTileCard(
    channel: TvChannel,
    isFav: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val stageColor = if (isDark) DarkTileStage else LightTileStage

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("channel_card_${channel.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(stageColor)
        ) {
            // Channel Logo or fallback monogram
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 18.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
                        .fillMaxWidth(0.78f)
                        .fillMaxHeight(0.75f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 14.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(PlusTvBrandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Favorite Star top-left with comfortable tap zone
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFav)
                    .padding(8.dp)
                    .testTag("favorite_star_${channel.id}"),
                contentAlignment = Alignment.TopStart
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.cd_toggle_favorite),
                    tint = if (isFav) StarGold else Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }

            // LIVE gradient badge top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(PlusTvBrandGradient)
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

        // Bottom Centered Channel Name
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )
    }
}
