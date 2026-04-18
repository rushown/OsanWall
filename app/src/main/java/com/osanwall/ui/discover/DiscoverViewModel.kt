package com.osanwall.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osanwall.data.model.*
import com.osanwall.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverUiState(
    val trendingMovies: List<Movie> = emptyList(),
    val suggestedUsers: List<User> = emptyList(),
    val searchResults: SearchResults = SearchResults(),
    val isLoading: Boolean = false,
    val query: String = ""
)

data class SearchResults(
    val users: List<User> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val songs: List<Song> = emptyList(),
    val books: List<Book> = emptyList()
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState = _uiState.asStateFlow()

    private val myId = authRepository.currentUserId ?: ""

    init {
        loadTrending()
        loadSuggested()
    }

    private fun loadTrending() {
        viewModelScope.launch {
            mediaRepository.getTrendingMovies().collect { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(trendingMovies = result.data) }
                }
            }
        }
    }

    private fun loadSuggested() {
        viewModelScope.launch {
            val users = userRepository.getSuggestedUsers(myId)
            _uiState.update { it.copy(suggestedUsers = users) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.length >= 2) search(query)
        else _uiState.update { it.copy(searchResults = SearchResults()) }
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val users = userRepository.searchUsers(query)
            _uiState.update { it.copy(searchResults = it.searchResults.copy(users = users)) }

            mediaRepository.searchMovies(query).collect { result ->
                if (result is Result.Success) {
                    _uiState.update {
                        it.copy(
                            searchResults = it.searchResults.copy(movies = result.data),
                            isLoading = false
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            mediaRepository.searchSongs(query).collect { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(searchResults = it.searchResults.copy(songs = result.data)) }
                }
            }
        }
        viewModelScope.launch {
            mediaRepository.searchBooks(query).collect { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(searchResults = it.searchResults.copy(books = result.data)) }
                }
            }
        }
    }
}
