package com.merowall.data.repository

import com.merowall.data.api.*
import com.merowall.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val spotifyApi: SpotifyApiService,
    private val spotifyAuth: SpotifyAuthService,
    private val tmdbApi: TmdbApiService,
    private val booksApi: GoogleBooksApiService
) {
    private var spotifyToken: String = ""
    private var tokenExpiry: Long = 0L

    private suspend fun getSpotifyToken(): String {
        if (System.currentTimeMillis() < tokenExpiry && spotifyToken.isNotEmpty()) return spotifyToken
        return try {
            val resp = spotifyAuth.getToken()
            spotifyToken = resp.access_token
            tokenExpiry = System.currentTimeMillis() + (resp.expires_in * 1000L) - 60000L
            spotifyToken
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Spotify token")
            ""
        }
    }

    fun searchSongs(query: String): Flow<Result<List<Song>>> = flow {
        emit(Result.Loading)
        try {
            val token = getSpotifyToken()
            if (token.isEmpty()) {
                emit(Result.Error("Spotify unavailable"))
                return@flow
            }
            val resp = spotifyApi.searchTracks(query)
            emit(Result.Success(resp.tracks?.items?.map { it.toSong() } ?: emptyList()))
        } catch (e: Exception) {
            Timber.e(e)
            emit(Result.Error(e.message ?: "Search failed", e))
        }
    }

    fun searchMovies(query: String): Flow<Result<List<Movie>>> = flow {
        emit(Result.Loading)
        try {
            val resp = tmdbApi.searchMovies(query)
            emit(Result.Success(resp.results.map { it.toMovie() }))
        } catch (e: Exception) {
            Timber.e(e)
            emit(Result.Error(e.message ?: "Search failed", e))
        }
    }

    fun getTrendingMovies(): Flow<Result<List<Movie>>> = flow {
        emit(Result.Loading)
        try {
            val resp = tmdbApi.getTrendingMovies()
            emit(Result.Success(resp.results.map { it.toMovie() }))
        } catch (e: Exception) {
            Timber.e(e)
            emit(Result.Error(e.message ?: "Failed to load trending", e))
        }
    }

    fun searchBooks(query: String): Flow<Result<List<Book>>> = flow {
        emit(Result.Loading)
        try {
            val resp = booksApi.searchBooks(query)
            emit(Result.Success(resp.items.map { it.toBook() }))
        } catch (e: Exception) {
            Timber.e(e)
            emit(Result.Error(e.message ?: "Search failed", e))
        }
    }
}
