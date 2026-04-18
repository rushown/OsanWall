package com.osanwall.data.repository

import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.osanwall.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao
) {
    private fun messagesRef(chatId: String) =
        database.getReference("${RtdbPaths.MESSAGES}/$chatId")

    private fun chatRef(chatId: String) =
        database.getReference("${RtdbPaths.CHATS}/$chatId")

    fun getChats(userId: String): Flow<List<Chat>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.CHATS)
            .orderByChild("${Fields.PARTICIPANTS}/$userId")
            .equalTo(true)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chats = snapshot.children.mapNotNull { child ->
                    runCatching { child.getValue(Chat::class.java)?.copy(id = child.key ?: "") }.getOrNull()
                }
                trySend(chats)
            }
            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException(), "getChats cancelled")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val ref = messagesRef(chatId)
            .orderByChild(Fields.TIMESTAMP)
            .limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { child ->
                    runCatching { child.getValue(Message::class.java)?.copy(id = child.key ?: "") }.getOrNull()
                }
                trySend(messages)
            }
            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException(), "getMessages cancelled")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getCachedMessages(chatId: String): Flow<List<Message>> =
        messageDao.getMessagesForChat(chatId).map { list ->
            list.map {
                Message(id = it.id, chatId = it.chatId, senderId = it.senderId,
                    content = it.content, type = MessageType.valueOf(it.type),
                    mediaUrl = it.mediaUrl, isRead = it.isRead, timestamp = it.timestamp)
            }
        }

    suspend fun sendMessage(chatId: String, message: Message) {
        try {
            val ref = messagesRef(chatId).push()
            val newMsg = message.copy(id = ref.key ?: "", timestamp = System.currentTimeMillis())
            ref.setValue(newMsg).await()
            chatRef(chatId).updateChildren(
                mapOf(
                    Fields.LAST_MESSAGE to message.content.take(100),
                    Fields.LAST_MESSAGE_TIME to newMsg.timestamp,
                    Fields.LAST_MESSAGE_SENDER_ID to message.senderId
                )
            ).await()
            messageDao.insertMessage(newMsg.toEntity())
        } catch (e: Exception) {
            Timber.e(e, "sendMessage failed for chat $chatId")
            throw e
        }
    }

    suspend fun markAsRead(chatId: String, myId: String) {
        try {
            messageDao.markMessagesRead(chatId, myId)
            chatRef(chatId).child("unreadCount/$myId").setValue(0).await()
        } catch (e: Exception) {
            Timber.e(e, "markAsRead failed")
        }
    }

    fun setTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        database.getReference("${RtdbPaths.TYPING}/$chatId/$userId")
            .setValue(isTyping)
    }

    fun getTypingStatus(chatId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val ref = database.getReference("${RtdbPaths.TYPING}/$chatId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typing = snapshot.children.associate { child ->
                    child.key!! to (child.getValue(Boolean::class.java) ?: false)
                }
                trySend(typing)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun setOnlineStatus(userId: String, isOnline: Boolean) {
        val ref = database.getReference("${RtdbPaths.PRESENCE}/$userId")
        val data = mapOf("isOnline" to isOnline, "lastSeen" to System.currentTimeMillis())
        ref.setValue(data)
        if (isOnline) {
            ref.onDisconnect().setValue(mapOf("isOnline" to false, "lastSeen" to ServerValue.TIMESTAMP))
        }
    }

    suspend fun getOrCreateChat(myId: String, otherId: String): String {
        val chatId = listOf(myId, otherId).sorted().joinToString("_")
        return try {
            val ref = chatRef(chatId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.setValue(
                    mapOf(
                        "id" to chatId,
                        Fields.PARTICIPANTS to mapOf(myId to true, otherId to true),
                        Fields.LAST_MESSAGE to "",
                        Fields.LAST_MESSAGE_TIME to 0L
                    )
                ).await()
            }
            chatId
        } catch (e: Exception) {
            Timber.e(e, "getOrCreateChat failed")
            chatId
        }
    }

    private fun Message.toEntity() = CachedMessage(
        id = id, chatId = chatId, senderId = senderId, content = content,
        type = type.name, mediaUrl = mediaUrl, isRead = isRead, timestamp = timestamp
    )
}
