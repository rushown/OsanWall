package com.merowall.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merowall.data.model.*
import com.merowall.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User = User(),
    val isLoading: Boolean = true,
    val isFollowing: Boolean = false,
    val isMe: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("userId")
        ?.takeIf { it != "me" }
        ?: authRepository.currentUserId
        ?: ""

    val myId: String = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(ProfileUiState(isMe = userId == myId || userId.isEmpty()))
    val uiState = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val targetId = userId.ifEmpty { myId }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepository.getUserFlow(targetId).collect { user ->
                val isFollowing = if (targetId != myId && myId.isNotEmpty()) {
                    userRepository.isFollowing(myId, targetId)
                } else false
                _uiState.update { it.copy(user = user, isLoading = false, isFollowing = isFollowing) }
            }
        }
    }

    fun toggleFollow() {
        val targetId = userId.ifEmpty { myId }
        viewModelScope.launch {
            val currently = _uiState.value.isFollowing
            try {
                if (currently) userRepository.unfollowUser(myId, targetId)
                else userRepository.followUser(myId, targetId)
                _uiState.update { it.copy(isFollowing = !currently) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateTopSongs(songs: List<Song>) {
        viewModelScope.launch { userRepository.updateTopSongs(myId, songs) }
    }

    fun updateTopMovies(movies: List<Movie>) {
        viewModelScope.launch { userRepository.updateTopMovies(myId, movies) }
    }

    fun updateTopBooks(books: List<Book>) {
        viewModelScope.launch { userRepository.updateTopBooks(myId, books) }
    }

    fun updateBio(bio: String) {
        viewModelScope.launch {
            userRepository.updateProfile(myId, mapOf("bio" to bio))
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
