package com.osanwall.data.api

import com.osanwall.data.model.Movie
import com.osanwall.data.model.Song
import com.osanwall.data.model.Book
import kotlinx.serialization.Serializable
import retrofit2.http.*

// ==================== SPOTIFY ====================
interface SpotifyApiService {
    @GET("v1/search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20
    ): SpotifySearchResponse

    @GET("v1/search")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 20
    ): SpotifySearchResponse
}

interface SpotifyAuthService {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun getToken(
        @Field("grant_type") grantType: String = "client_credentials"
    ): SpotifyTokenResponse
}

@Serializable
data class SpotifyTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTracks? = null,
    val artists: SpotifyArtists? = null
)

@Serializable
data class SpotifyTracks(val items: List<SpotifyTrack> = emptyList())

@Serializable
data class SpotifyArtists(val items: List<SpotifyArtist> = emptyList())

@Serializable
data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>,
    val album: SpotifyAlbum,
    val preview_url: String? = null,
    val external_urls: Map<String, String> = emptyMap()
) {
    fun toSong() = Song(
        id = id,
        title = name,
        artist = artists.firstOrNull()?.name ?: "",
        album = album.name,
        albumArtUrl = album.images.firstOrNull()?.url ?: "",
        previewUrl = preview_url ?: "",
        spotifyUrl = external_urls["spotify"] ?: ""
    )
}

@Serializable
data class SpotifyAlbum(
    val name: String,
    val images: List<SpotifyImage> = emptyList()
)

@Serializable
data class SpotifyArtist(
    val id: String = "",
    val name: String
)

@Serializable
data class SpotifyImage(val url: String, val width: Int = 0, val height: Int = 0)

// ==================== TMDB ====================
interface TmdbApiService {
    @GET("3/search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbMovieResponse

    @GET("3/trending/movie/week")
    suspend fun getTrendingMovies(@Query("page") page: Int = 1): TmdbMovieResponse

    @GET("3/movie/popular")
    suspend fun getPopularMovies(@Query("page") page: Int = 1): TmdbMovieResponse
}

@Serializable
data class TmdbMovieResponse(
    val results: List<TmdbMovie> = emptyList(),
    val total_pages: Int = 0,
    val total_results: Int = 0
)

@Serializable
data class TmdbMovie(
    val id: Int,
    val title: String,
    val overview: String,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String = "",
    val vote_average: Double = 0.0,
    val genre_ids: List<Int> = emptyList()
) {
    fun toMovie() = Movie(
        id = id.toString(),
        title = title,
        overview = overview,
        posterUrl = if (poster_path != null) "https://image.tmdb.org/t/p/w500$poster_path" else "",
        backdropUrl = if (backdrop_path != null) "https://image.tmdb.org/t/p/w780$backdrop_path" else "",
        releaseYear = release_date.take(4),
        rating = vote_average
    )
}

// ==================== GOOGLE BOOKS ====================
interface GoogleBooksApiService {
    @GET("books/v1/volumes")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 20
    ): GoogleBooksResponse
}

@Serializable
data class GoogleBooksResponse(
    val items: List<GoogleBookItem> = emptyList(),
    val totalItems: Int = 0
)

@Serializable
data class GoogleBookItem(
    val id: String,
    val volumeInfo: GoogleBookVolumeInfo
) {
    fun toBook() = Book(
        id = id,
        title = volumeInfo.title,
        author = volumeInfo.authors?.firstOrNull() ?: "Unknown",
        coverUrl = volumeInfo.imageLinks?.thumbnail?.replace("http://", "https://") ?: "",
        description = volumeInfo.description ?: "",
        publishedYear = volumeInfo.publishedDate?.take(4) ?: "",
        pageCount = volumeInfo.pageCount ?: 0
    )
}

@Serializable
data class GoogleBookVolumeInfo(
    val title: String,
    val authors: List<String>? = null,
    val description: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val imageLinks: GoogleBookImageLinks? = null
)

@Serializable
data class GoogleBookImageLinks(
    val thumbnail: String? = null,
    val smallThumbnail: String? = null
)
