package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.M3uParser
import com.example.data.TvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 👇 YAHAN APNA GIST M3U LINK PASTE KAREIN (Future mein badalna ho toh bas isi line ko edit karein)
const val DEFAULT_M3U_URL = "https://gist.githubusercontent.com/MaheshAmbaliya/dc31a55aa2985aeec9f9c432d313d48c/raw/playlists.json"

private const val PREFS_NAME = "plustv_prefs"
private const val KEY_DARK_MODE = "dark_mode"
private const val KEY_FAVORITES = "favorites"

data class PlusTvUiState(
    val isDark: Boolean = true,
    val playlistUrl: String = DEFAULT_M3U_URL,
    val channels: List<TvChannel> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshingChannel: Boolean = false,
    val errorText: String? = null,
    val selectedCategory: String = "All",
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val activeChannel: TvChannel? = null
) {
    val categories: List<String>
        get() {
            val priority = listOf("Entertainment", "Sports", "Movies", "News", "Kids", "Music")
            val grouped = channels.groupingBy { it.group }.eachCount()
            return listOf("All") + grouped.keys.sortedWith(
                compareBy<String> {
                    val idx = priority.indexOf(it)
                    if (idx == -1) Int.MAX_VALUE else idx
                }.thenByDescending { grouped[it] ?: 0 }
            )
        }

    val filteredChannels: List<TvChannel>
        get() {
            val q = searchQuery.trim().lowercase()
            return channels.filter { ch ->
                if (showFavoritesOnly && !favorites.contains(ch.id)) return@filter false
                if (selectedCategory != "All" && ch.group != selectedCategory) return@filter false
                if (q.isNotEmpty() &&
                    !ch.name.lowercase().contains(q) &&
                    !ch.group.lowercase().contains(q)
                ) {
                    return@filter false
                }
                true
            }
        }
}

class PlusTvViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        PlusTvUiState(
            isDark = prefs.getBoolean(KEY_DARK_MODE, true),
            playlistUrl = DEFAULT_M3U_URL,
            favorites = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
        )
    )
    val uiState: StateFlow<PlusTvUiState> = _uiState.asStateFlow()

    init {
        loadPlaylist(DEFAULT_M3U_URL)
    }

    fun loadPlaylist(url: String = DEFAULT_M3U_URL) {
        val targetUrl = url.trim().ifBlank { DEFAULT_M3U_URL }
        _uiState.update {
            it.copy(
                playlistUrl = targetUrl,
                isLoading = true,
                errorText = null
            )
        }
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val raw = M3uParser.fetchPlaylistText(targetUrl)
                    M3uParser.parse(raw)
                }
                _uiState.update {
                    it.copy(
                        channels = parsed,
                        isLoading = false,
                        errorText = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorText = e.message ?: "Failed to load playlist"
                    )
                }
            }
        }
    }

    fun refreshActiveChannel() {
        val current = _uiState.value.activeChannel ?: return
        _uiState.update { it.copy(isRefreshingChannel = true) }
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val raw = M3uParser.fetchPlaylistText(DEFAULT_M3U_URL)
                    M3uParser.parse(raw)
                }
                val matched = parsed.firstOrNull { ch ->
                    (current.tvgId.isNotBlank() && ch.tvgId == current.tvgId) ||
                        ch.name.equals(current.name, ignoreCase = true)
                } ?: current

                _uiState.update {
                    it.copy(
                        channels = parsed,
                        activeChannel = matched,
                        isRefreshingChannel = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isRefreshingChannel = false) }
            }
        }
    }

    fun toggleTheme() {
        val nextDark = !_uiState.value.isDark
        prefs.edit().putBoolean(KEY_DARK_MODE, nextDark).apply()
        _uiState.update { it.copy(isDark = nextDark) }
    }

    fun toggleSearch() {
        _uiState.update { current ->
            val nextOpen = !current.searchOpen
            current.copy(
                searchOpen = nextOpen,
                searchQuery = if (nextOpen) current.searchQuery else ""
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: String) {
        _uiState.update {
            it.copy(
                showFavoritesOnly = false,
                selectedCategory = category
            )
        }
    }

    fun toggleFavoritesFilter() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun setFavoritesFilter(enabled: Boolean) {
        _uiState.update {
            it.copy(
                showFavoritesOnly = enabled,
                selectedCategory = if (enabled) it.selectedCategory else "All"
            )
        }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedCategory = "All",
                showFavoritesOnly = false,
                searchQuery = ""
            )
        }
    }

    fun toggleFavorite(channelId: String) {
        _uiState.update { current ->
            val next = if (current.favorites.contains(channelId)) {
                current.favorites - channelId
            } else {
                current.favorites + channelId
            }
            prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
            current.copy(favorites = next)
        }
    }

    fun openChannel(channel: TvChannel) {
        _uiState.update { it.copy(activeChannel = channel) }
    }

    fun closePlayer() {
        _uiState.update { it.copy(activeChannel = null, isRefreshingChannel = false) }
    }
}
