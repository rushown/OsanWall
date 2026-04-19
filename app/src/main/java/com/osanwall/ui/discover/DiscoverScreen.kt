package com.osanwall.ui.discover


import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.osanwall.data.model.*
import com.osanwall.ui.components.GlassCard
import com.osanwall.ui.components.PressableScaleBox
import com.osanwall.ui.components.UserAvatar
import com.osanwall.ui.components.shimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateToProfile: (String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(0.35f))
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search bar
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Discover Ethereal Moments",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SearchBar(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState.query.length >= 2) {
                // Search results
                if (uiState.searchResults.users.isNotEmpty()) {
                    item {
                        SectionHeader("People")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.searchResults.users) { user ->
                                UserChip(user = user, onClick = { onNavigateToProfile(user.id) })
                            }
                        }
                    }
                }
                if (uiState.searchResults.movies.isNotEmpty()) {
                    item { SectionHeader("Movies") }
                    items(uiState.searchResults.movies.take(5)) { movie ->
                        MovieSearchItem(movie)
                    }
                }
                if (uiState.searchResults.songs.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    items(uiState.searchResults.songs.take(5)) { song ->
                        SongSearchItem(song)
                    }
                }
            } else {
                // Suggested Users
                if (uiState.suggestedUsers.isNotEmpty()) {
                    item {
                        SectionHeader("Suggested Creators")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.suggestedUsers) { user ->
                                SuggestedUserCard(user = user, onClick = { onNavigateToProfile(user.id) })
                            }
                        }
                    }
                }

                // Trending Movies Grid
                if (uiState.trendingMovies.isNotEmpty()) {
                    item { SectionHeader("Trending Now") }
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            modifier = Modifier.fillMaxWidth().height(500.dp).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            userScrollEnabled = false
                        ) {
                            items(uiState.trendingMovies) { movie ->
                                Box(modifier = Modifier.aspectRatio(0.67f).clip(RoundedCornerShape(12.dp))) {
                                    AsyncImage(
                                        model = movie.posterUrl,
                                        contentDescription = movie.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(0.55f))
                                                )
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            movie.title,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item { SectionHeader("Trending Now") }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.67f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                }

                // Category tiles
                item {
                    SectionHeader("Explore by Category")
                    val categories = listOf(
                        Triple("Digital Art", Icons.Default.Palette, MaterialTheme.colorScheme.primary),
                        Triple("Short Films", Icons.Default.Movie, MaterialTheme.colorScheme.secondary),
                        Triple("Podcasts", Icons.Default.Mic, MaterialTheme.colorScheme.tertiary),
                        Triple("AI Dreams", Icons.Default.AutoAwesome, MaterialTheme.colorScheme.error)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(categories) { (label, icon, color) ->
                            CategoryTile(label = label, icon = icon, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search artists, movies, vibes...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, null) } }
        } else null,
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
fun SuggestedUserCard(user: User, onClick: () -> Unit) {
    GlassCard(onClick = onClick, modifier = Modifier.width(150.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UserAvatar(imageUrl = user.avatarUrl, size = 64.dp, hasGradientBorder = user.isVerified)
            Text(user.username, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.12f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(0.4f))
            ) {
                Text("Follow", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun UserChip(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserAvatar(imageUrl = user.avatarUrl, size = 32.dp)
        Text(user.username, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MovieSearchItem(movie: Movie) {
    ListItem(
        headlineContent = { Text(movie.title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text("${movie.releaseYear} · ⭐ ${String.format("%.1f", movie.rating)}") },
        leadingContent = {
            AsyncImage(
                model = movie.posterUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(50.dp, 75.dp).clip(RoundedCornerShape(8.dp))
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun SongSearchItem(song: Song) {
    ListItem(
        headlineContent = { Text(song.title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text("${song.artist} · ${song.album}") },
        leadingContent = {
            AsyncImage(
                model = song.albumArtUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
            )
        },
        trailingContent = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun CategoryTile(label: String, icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.12f))
            .border(0.5.dp, color.copy(0.3f), RoundedCornerShape(20.dp))
            .clickable {}
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}
