package com.osanwall.data.mapper

import com.osanwall.data.model.*
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─── Timestamp helpers ────────────────────────────────────────────────────────

fun Long.toRelativeTimeString(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1)   -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1)    -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7)    -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
    }
}

fun Long.toTimeString(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))

// ─── CachedPost ↔ Post ────────────────────────────────────────────────────────
//
//  The mismatch explained:
//    CachedPost.timestamp  = Long   (epoch ms, stored in Room)
//    Post.timestamp        = String (human-readable, used in UI)
//    CachedPost stores author as flat fields; Post embeds a User object.

fun CachedPost.toPost(): Post = Post(
    id            = id,
    author        = User(
        id        = userId,
        username  = userUsername,
        avatarUrl = userAvatarUrl
    ),
    type          = runCatching { PostType.valueOf(type) }.getOrDefault(PostType.TEXT),
    content       = content,
    timestamp     = timestamp.toRelativeTimeString(),   // Long → "2h ago"
    likesCount    = likesCount,
    commentsCount = commentsCount,
    mediaUrl      = mediaDataJson?.let {
        // mediaDataJson may contain an imageUrl; extract it for the legacy field
        runCatching {
            Json.decodeFromString(MediaDataJsonSurrogate.serializer(), it).imageUrl
        }.getOrNull()
    }
)

fun Post.toCachedPost(): CachedPost = CachedPost(
    id            = id,
    userId        = author.id,
    userUsername  = author.username,
    userAvatarUrl = author.avatarUrl,
    type          = type.name,
    content       = content,
    mediaDataJson = mediaUrl?.let { """{"imageUrl":"$it"}""" },
    likesCount    = likesCount,
    commentsCount = commentsCount,
    isLiked       = false,
    timestamp     = System.currentTimeMillis()          // String timestamp not storable; use now()
)

// ─── CachedMessage ↔ Message ──────────────────────────────────────────────────
//
//  Both use Long for timestamp — no conversion needed there.
//  MessageType is stored as its enum name string in Room.

fun CachedMessage.toMessage(): Message = Message(
    id        = id,
    chatId    = chatId,
    senderId  = senderId,
    content   = content,
    timestamp = timestamp,                                          // Long ✓
    isRead    = isRead,
    type      = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT),
    mediaUrl  = mediaUrl
)

fun Message.toCachedMessage(): CachedMessage = CachedMessage(
    id        = id,
    chatId    = chatId,
    senderId  = senderId,
    content   = content,
    type      = type.name,
    mediaUrl  = mediaUrl,
    isRead    = isRead,
    timestamp = timestamp                                           // Long ✓
)

// ─── CachedChat ↔ Chat ────────────────────────────────────────────────────────
//
//  CachedChat has no otherUser — call toChat(otherUser) after looking up
//  the user from CachedUser by the other participant's id.
//  Chat.lastMessageTime is a String; CachedChat.lastMessageTime is Long.

fun CachedChat.toChat(otherUser: User): Chat = Chat(
    id              = id,
    otherUser       = otherUser,
    lastMessage     = lastMessage,
    lastMessageTime = lastMessageTime.toTimeString(),               // Long → "14:30"
    unreadCount     = unreadCount
)

fun Chat.toCachedChat(lastMessageSenderId: String = ""): CachedChat = CachedChat(
    id                  = id,
    lastMessage         = lastMessage,
    lastMessageTime     = System.currentTimeMillis(),               // String not storable; use now()
    lastMessageSenderId = lastMessageSenderId,
    unreadCount         = unreadCount
)

// ─── CachedUser ↔ User ───────────────────────────────────────────────────────

fun CachedUser.toUser(): User = User(
    id        = id,
    username  = username,
    avatarUrl = avatarUrl
)

fun User.toCachedUser(): CachedUser = CachedUser(
    id        = id,
    username  = username,
    avatarUrl = avatarUrl
)

// ─── Internal surrogate for mediaDataJson ────────────────────────────────────

@kotlinx.serialization.Serializable
internal data class MediaDataJsonSurrogate(
    val imageUrl: String? = null
)