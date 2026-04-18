package com.merowall.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.merowall.data.model.*
import com.merowall.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isRefreshing: Boolean = false,
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

    private val userId = authRepository.currentUserId ?: ""

    val feedPosts: Flow<PagingData<Post>> = postRepository.getFeedPosts(userId)
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
            postRepository.likePost(postId, userId, !isLiked)
        }
    }

    fun createPost(type: PostType, content: String, mediaData: MediaData? = null) {
        viewModelScope.launch {
            try {
                val post = Post(
                    userId = userId,
                    type = type,
                    content = content,
                    mediaData = mediaData,
                    timestamp = System.currentTimeMillis()
                )
                postRepository.createPost(post)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            postRepository.deletePost(postId)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
