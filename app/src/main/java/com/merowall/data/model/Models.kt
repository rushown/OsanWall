package com.merowall.data.model

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val coverUrl: String = "",
    val location: String = "",
    val website: String = "",
    val topSongs: List<Song> = emptyList(),
    val topMovies: List<Movie> = emptyList(),
    val topBooks: List<Book> = emptyList(),
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val isVerified: Boolean = false,
    val isPrivate: Boolean = false,
    val fcmToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Post(
    val id: String = "",
    val userId: String = "",
    val userUsername: String = "",
    val userAvatarUrl: String = "",
    val type: PostType = PostType.THOUGHT,
    val content: String = "",
    val mediaData: MediaData? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val isLiked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PostType { THOUGHT, SONG, MOVIE, BOOK }

@Serializable
data class MediaData(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val imageUrl: String = "",
    val externalUrl: String = "",
    val extraInfo: String = ""
)

@Serializable
data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUrl: String = "",
    val previewUrl: String = "",
    val spotifyUrl: String = ""
)

@Serializable
data class Movie(
    val id: String = "",
    val title: String = "",
    val overview: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val releaseYear: String = "",
    val rating: Double = 0.0,
    val genres: List<String> = emptyList()
)

@Serializable
data class Book(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val coverUrl: String = "",
    val description: String = "",
    val publishedYear: String = "",
    val pageCount: Int = 0
)

@Serializable
data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val content: String = "",
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String = "",
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageType { TEXT, IMAGE, AUDIO }

@Serializable
data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantInfo: Map<String, UserPreview> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCount: Int = 0
)

@Serializable
data class UserPreview(
    val id: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val isOnline: Boolean = false
)

@Serializable
data class Comment(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val userAvatarUrl: String = "",
    val content: String = "",
    val likesCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
