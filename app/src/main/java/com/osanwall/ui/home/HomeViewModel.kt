package com.osanwall.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.osanwall.data.model.*
import com.osanwall.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val isPosting: Boolean = false,
    /** Bumped after a successful post so the UI can refresh the feed. */
    val feedVersion: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    /** Always read fresh so posting works right after sign-in. */
    private val currentUserId: String get() = authRepository.currentUserId ?: ""

    val feedPosts: Flow<PagingData<Post>> = postRepository.getFeedPosts(currentUserId)
        .cachedIn(viewModelScope)

    private val _trendingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val trendingMovies = _trendingMovies.asStateFlow()

    init {
        loadTrendingMovies()
    }

    private fun loadTrendingMovies() {
        viewModelScope.launch {
            mediaRepository.getTrendingMovies().collect { result ->
                when (result) {
                    is Result.Success -> _trendingMovies.value = result.data.take(6)
                    is Result.Error -> { /* silent fail for trending */ }
                    else -> {}
                }
            }
        }
    }

    fun likePost(postId: String, currentLikeCount: Int, isLiked: Boolean) {
        viewModelScope.launch {
            postRepository.likePost(postId, currentUserId, !isLiked)
        }
    }

    fun createPost(type: PostType, content: String, mediaData: MediaData? = null) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, error = null) }
            try {
                val user = authRepository.fetchUser(currentUserId)
                val post = Post(
                    userId = currentUserId,
                    userUsername = user.username.ifBlank { "user" },
                    userAvatarUrl = user.avatarUrl,
                    type = type,
                    content = content.trim().take(2000),
                    mediaData = mediaData,
                    timestamp = System.currentTimeMillis()
                )
                postRepository.createPost(post)
                _uiState.update {
                    it.copy(isPosting = false, feedVersion = it.feedVersion + 1, error = null)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPosting = false, error = e.message) }
            }
        }
    }

    /** Short thought for the home composer (same pipeline as [createPost]). */
    fun postThought(text: String) {
        val trimmed = text.trim()
        if (currentUserId.isEmpty() || trimmed.isEmpty() || trimmed.length > 2000) return
        createPost(PostType.THOUGHT, trimmed)
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            postRepository.deletePost(postId, currentUserId)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
