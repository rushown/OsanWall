package com.osanwall.ui.profile


import androidx.compose.foundation.*
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
import com.osanwall.ui.components.*
import com.osanwall.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Cover + Avatar
        item {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model = user.coverUrl.ifEmpty { null },
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
                // Back button
                if (!uiState.isMe) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                } else {
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, tint = Color.White)
                    }
                }
            }
        }

        // Profile Info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-40).dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar with glow
                    Box {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)))
                                .padding(3.dp)
                        ) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        }
                    }

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.isMe) {
                            OutlinedButton(onClick = {}) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit")
                            }
                        } else {
                            GradientButton(
                                text = if (uiState.isFollowing) "Following" else "Follow",
                                onClick = { viewModel.toggleFollow() }
                            )
                            OutlinedButton(onClick = { onOpenChat(userId) }) {
                                Icon(Icons.Default.Message, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    if (user.isVerified) {
                        Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    }
                }
                if (user.bio.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(user.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                // Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileStat("Posts", user.postsCount, Modifier.weight(1f))
                    ProfileStat("Followers", user.followersCount, Modifier.weight(1f))
                    ProfileStat("Following", user.followingCount, Modifier.weight(1f))
                }
            }
        }

        // Featured Songs
        if (user.topSongs.isNotEmpty()) {
            item {
                ProfileSection(title = "Featured Songs", icon = Icons.Default.LibraryMusic, accentColor = MaterialTheme.colorScheme.primary) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(user.topSongs) { song ->
                            SongCard(song)
                        }
                    }
                }
            }
        }

        // Featured Movies
        if (user.topMovies.isNotEmpty()) {
            item {
                ProfileSection(title = "Featured Movies", icon = Icons.Default.Movie, accentColor = MaterialTheme.colorScheme.secondary) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(user.topMovies) { movie ->
                            MovieCard(movie)
                        }
                    }
                }
            }
        }

        // Featured Books
        if (user.topBooks.isNotEmpty()) {
            item {
                ProfileSection(title = "Featured Books", icon = Icons.Default.MenuBook, accentColor = MaterialTheme.colorScheme.tertiary) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(user.topBooks) { book ->
                            BookCard(book)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
fun ProfileStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProfileSection(title: String, icon: ImageVector, accentColor: Color, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = {}) { Text("See All", color = accentColor) }
        }
        content()
    }
}

@Composable
fun SongCard(song: Song) {
    Column(modifier = Modifier.width(140.dp)) {
        AsyncImage(
            model = song.albumArtUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MovieCard(movie: Movie) {
    AsyncImage(
        model = movie.posterUrl,
        contentDescription = movie.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(14.dp))
    )
}

@Composable
fun BookCard(book: Book) {
    AsyncImage(
        model = book.coverUrl,
        contentDescription = book.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(14.dp))
    )
}
