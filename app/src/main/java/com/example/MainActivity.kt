package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.PlayerScreen
import com.example.ui.PlusTvHomeScreen
import com.example.ui.PlusTvViewModel
import com.example.ui.theme.PlusTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlusTvRootApp()
        }
    }
}

@Composable
fun PlusTvRootApp(
    viewModel: PlusTvViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlusTvTheme(darkTheme = uiState.isDark) {
        val activeChannel = uiState.activeChannel
        if (activeChannel != null) {
            BackHandler { viewModel.closePlayer() }
            val relatedChannels = remember(uiState.channels, activeChannel.group) {
                uiState.channels
                    .filter { it.group == activeChannel.group }
                    .take(30)
            }
            PlayerScreen(
                channel = activeChannel,
                isFavorite = uiState.favorites.contains(activeChannel.id),
                isRefreshingChannel = uiState.isRefreshingChannel,
                relatedChannels = relatedChannels,
                onSelectChannel = viewModel::openChannel,
                onToggleFavorite = { viewModel.toggleFavorite(activeChannel.id) },
                onRefreshChannel = viewModel::refreshActiveChannel,
                onBack = viewModel::closePlayer
            )
        } else {
            PlusTvHomeScreen(
                uiState = uiState,
                onLoadPlaylist = viewModel::loadPlaylist,
                onToggleTheme = viewModel::toggleTheme,
                onToggleSearch = viewModel::toggleSearch,
                onUpdateSearchQuery = viewModel::updateSearchQuery,
                onSelectCategory = viewModel::selectCategory,
                onToggleFavoritesFilter = viewModel::toggleFavoritesFilter,
                onClearFilters = viewModel::clearFilters,
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenChannel = viewModel::openChannel
            )
        }
    }
}
