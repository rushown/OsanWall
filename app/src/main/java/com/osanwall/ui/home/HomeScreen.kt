package com.osanwall.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.osanwall.data.model.Post
import com.osanwall.ui.components.GlassCard
import com.osanwall.ui.components.PressableScaleBox
import com.osanwall.ui.components.UserAvatar
import com.osanwall.ui.components.shimmerEffect
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenExplore: () -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    onOpenMyProfile: () -> Unit = {},
    isLoggedIn: Boolean = false,
    onRequestAuth: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val trending by viewModel.trendingMovies.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val feedPosts = viewModel.feedPosts.collectAsLazyPagingItems()
    val suggestedCreators = listOf("@LunaSky", "@Vertex", "@NeonJace", "@Minimal_")

    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    var showComposeSheet by remember { mutableStateOf(false) }
    val composeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val composeFocus = remember { FocusRequester() }

    LaunchedEffect(uiState.feedVersion) {
        if (uiState.feedVersion > 0) {
            composerText = ""
            feedPosts.refresh()
            showComposeSheet = false
        }
    }

    LaunchedEffect(showComposeSheet) {
        if (showComposeSheet) {
            delay(280)
            composeFocus.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.clickable(onClick = onOpenMyProfile)) {
                                UserAvatar(imageUrl = "", size = 40.dp, hasGradientBorder = true)
                            }
                            Text(
                                "OsanWall",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onOpenExplore) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                CreatePostPromptRow(
                    isLoggedIn = isLoggedIn,
                    onOpenComposer = { showComposeSheet = true },
                    onRequestAuth = onRequestAuth
                )
            }

            item {
                Text(
                    "Feed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when (val refresh = feedPosts.loadState.refresh) {
                is LoadState.Loading -> if (feedPosts.itemCount == 0) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }
                is LoadState.Error -> item {
                    Text(
                        refresh.error.localizedMessage ?: "Could not load feed",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                else -> Unit
            }

            items(
                count = feedPosts.itemCount,
                key = { index -> feedPosts[index]?.id ?: "post_$index" }
            ) { index ->
                val post = feedPosts[index]
                if (post != null) {
                    FeedPostCard(post = post, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            if (feedPosts.loadState.append is LoadState.Loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (feedPosts.loadState.refresh is LoadState.NotLoading &&
                feedPosts.itemCount == 0 &&
                feedPosts.loadState.append.endOfPaginationReached
            ) {
                item {
                    Text(
                        "No posts yet. Say hello above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            uiState.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            item {
                SectionTitle("Suggested Creators", "View All")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(suggestedCreators) { creator ->
                        GlassCard(modifier = Modifier.width(132.dp), onClick = { onOpenProfile(creator) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UserAvatar(imageUrl = "", size = 60.dp, hasGradientBorder = true)
                                Text(creator, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = {},
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary.copy(0.14f))
                                ) {
                                    Text("Follow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle("Trending Songs", "See All")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(4) { idx ->
                        GlassCard(modifier = Modifier.width(210.dp), onClick = onOpenExplore) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest).shimmerEffect()
                                )
                                Text("Midnight Resonance ${idx + 1}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Lumina Collective", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle("Trending Movies", "Browse")
                if (trending.isEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(0.66f).clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).shimmerEffect()
                            )
                        }
                    }
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(trending.take(8), key = { "${it.id}_${it.title}" }) { movie ->
                            PressableScaleBox(
                                modifier = Modifier.width(140.dp).aspectRatio(0.66f).clip(RoundedCornerShape(14.dp)),
                                onClick = onOpenExplore
                            ) {
                                AsyncImage(model = movie.posterUrl, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(
                                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))).padding(8.dp)
                                ) {
                                    Text(movie.title, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showComposeSheet && isLoggedIn) {
            ModalBottomSheet(
                onDismissRequest = { showComposeSheet = false },
                sheetState = composeSheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp
            ) {
                CreatePostComposerSheet(
                    text = composerText,
                    onTextChange = { composerText = it },
                    isPosting = uiState.isPosting,
                    onDismiss = { showComposeSheet = false },
                    onPost = { viewModel.postThought(composerText) },
                    focusRequester = composeFocus
                )
            }
        }
    }
}

@Composable
private fun CreatePostPromptRow(
    isLoggedIn: Boolean,
    onOpenComposer: () -> Unit,
    onRequestAuth: () -> Unit
) {
    val frosted = Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.48f),
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.38f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.44f)
        )
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable {
                if (isLoggedIn) onOpenComposer() else onRequestAuth()
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(frosted)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(imageUrl = "", size = 40.dp, hasGradientBorder = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isLoggedIn) "What's on your mind?"
                    else "Sign in to share what's on your mind…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun CreatePostComposerSheet(
    text: String,
    onTextChange: (String) -> Unit,
    isPosting: Boolean,
    onDismiss: () -> Unit,
    onPost: () -> Unit,
    focusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Text(
                "New post",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = onPost,
                enabled = text.isNotBlank() && !isPosting
            ) {
                Text("Post", fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            UserAvatar(imageUrl = "", size = 44.dp, hasGradientBorder = true)
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 2000) onTextChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("What's on your mind?") },
                minLines = 4,
                maxLines = 12,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && !isPosting) onPost() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${text.length}/2000",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun FeedPostCard(post: Post, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(imageUrl = post.userAvatarUrl, size = 42.dp, hasGradientBorder = false)
                Column(Modifier.weight(1f)) {
                    Text(
                        post.userUsername.ifBlank { "Member" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        DateUtils.getRelativeTimeSpanString(
                            post.timestamp,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                post.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("${post.likesCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubble, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text("${post.commentsCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}
