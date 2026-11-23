package com.plustv.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_M3U_URL = "https://jiobkl.ytzinadabaad.workers.dev/"
private const val PREFS_NAME = "plustv_prefs"

private val BrandGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00D2FF), Color(0xFF3B82F6), Color(0xFFC026D3), Color(0xFFEC4899))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlusTvApp()
        }
    }
}

@Composable
fun PlusTvApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var isDark by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
    var playlistUrl by remember {
        mutableStateOf(prefs.getString("playlist_url", DEFAULT_M3U_URL) ?: DEFAULT_M3U_URL)
    }
    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf("All") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var activeChannel by remember { mutableStateOf<TvChannel?>(null) }

    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }

    fun loadPlaylist(url: String) {
        scope.launch {
            loading = true
            errorText = null
            prefs.edit().putString("playlist_url", url).apply()
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val raw = M3uParser.fetchPlaylistText(url)
                    M3uParser.parse(raw)
                }
                channels = parsed
            } catch (e: Exception) {
                errorText = e.message ?: "Failed to load playlist"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadPlaylist(playlistUrl) }

    // If watching a channel, show full-screen Media3 ExoPlayer
    activeChannel?.let { ch ->
        BackHandler { activeChannel = null }
        PlayerScreen(channel = ch, onBack = { activeChannel = null })
        return
    }

    val bgColor = if (isDark) Color.Black else Color(0xFFF4F6FB)
    val surfaceColor = if (isDark) Color(0xFF14141A) else Color.White
    val borderColor = if (isDark) Color(0xFF262632) else Color(0xFFDFE4EE)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val categories = remember(channels) {
        val priority = listOf("Entertainment", "Sports", "Movies", "News", "Kids", "Music")
        val grouped = channels.groupingBy { it.group }.eachCount()
        listOf("All") + grouped.keys.sortedWith(compareBy<String> {
            val idx = priority.indexOf(it)
            if (idx == -1) Int.MAX_VALUE else idx
        }.thenByDescending { grouped[it] ?: 0 })
    }

    val filtered = remember(channels, selectedCategory, searchQuery, showFavoritesOnly, favorites) {
        val q = searchQuery.trim().lowercase()
        channels.filter { ch ->
            if (showFavoritesOnly && !favorites.contains(ch.id)) return@filter false
            if (selectedCategory != "All" && ch.group != selectedCategory) return@filter false
            if (q.isNotEmpty() && !ch.name.lowercase().contains(q) && !ch.group.lowercase().contains(q)) {
                return@filter false
            }
            true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // + PLUSTV Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(surfaceColor)
                        .border(1.dp, borderColor, CircleShape)
                        .clickable { showUrlDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Playlist Settings", tint = textPrimary)
                }

                Text(
                    text = "+ PLUSTV",
                    style = TextStyle(
                        brush = BrandGradient,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(surfaceColor)
                        .border(1.dp, borderColor, CircleShape)
                        .clickable {
                            isDark = !isDark
                            prefs.edit().putBoolean("dark_mode", isDark).apply()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Theme",
                        tint = textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (searchOpen) Color(0xFF00D2FF) else surfaceColor)
                        .border(1.dp, borderColor, CircleShape)
                        .clickable { searchOpen = !searchOpen },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchOpen) Color.White else textPrimary
                    )
                }
            }
        }

        HorizontalDivider(color = borderColor, thickness = 1.dp)

        // Expandable Search Input
        if (searchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Star Plus, Sony SAB, Colors...", color = textMuted) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(999.dp)
            )
        }

        // Subheader: Live Streaming Now + Gradient Count Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (showFavoritesOnly) "Favorite Channels" else "Live Streaming Now",
                color = textMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(BrandGradient)
                    .clickable { showFavoritesOnly = !showFavoritesOnly }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${filtered.size} Channels",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Horizontal Category Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.take(15).forEach { cat ->
                val active = selectedCategory == cat && !showFavoritesOnly
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .then(
                            if (active) Modifier.background(BrandGradient)
                            else Modifier
                                .background(surfaceColor)
                                .border(1.dp, borderColor, RoundedCornerShape(999.dp))
                        )
                        .clickable {
                            showFavoritesOnly = false
                            selectedCategory = cat
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (active) Color.White else textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Main 3-Column Channel Grid
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00D2FF))
            }

            errorText != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorText ?: "Error", color = Color(0xFFF87171), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { loadPlaylist(playlistUrl) }) { Text("Retry") }
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
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
                                .background(BrandGradient)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (showFavoritesOnly) "Favorites" else selectedCategory,
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(filtered, key = { it.id }) { channel ->
                    val isFav = favorites.contains(channel.id)
                    PlusTvTileCard(
                        channel = channel,
                        isFav = isFav,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        onClick = { activeChannel = channel },
                        onToggleFav = {
                            val next = if (isFav) favorites - channel.id else favorites + channel.id
                            favorites = next
                            prefs.edit().putStringSet("favorites", next).apply()
                        }
                    )
                }
            }
        }
    }

    // M3U Playlist URL Dialog
    if (showUrlDialog) {
        var draft by remember { mutableStateOf(playlistUrl) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("M3U Playlist URL") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text("https://.../playlist.m3u") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = draft.trim()
                    if (trimmed.isNotEmpty()) {
                        playlistUrl = trimmed
                        loadPlaylist(trimmed)
                    }
                    showUrlDialog = false
                }) { Text("Load") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlusTvTileCard(
    channel: TvChannel,
    isFav: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    onClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color(0xFF040407))
                .padding(8.dp)
        ) {
            // Favorite Star top-left
            Icon(
                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFav) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(20.dp)
                    .clickable(onClick = onToggleFav)
            )

            // LIVE gradient badge top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(999.dp))
                    .background(BrandGradient)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }

            // Channel Logo
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 14.dp)
                    .fillMaxWidth(0.75f)
                    .fillMaxHeight(0.75f)
            )
        }

        // Bottom Centered Channel Name
        Text(
            text = channel.name,
            color = textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 9.dp)
        )
    }
}
