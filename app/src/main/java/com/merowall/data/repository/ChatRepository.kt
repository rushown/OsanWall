package com.merowall.data.repository

import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.merowall.data.model.*
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
    fun getChats(userId: String): Flow<List<Chat>> = callbackFlow {
        val ref = database.getReference("chats").orderByChild("participants/$userId").equalTo(true)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chats = snapshot.children.mapNotNull { child ->
                    child.getValue(Chat::class.java)?.copy(id = child.key ?: "")
                }
                trySend(chats)
            }
            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val ref = database.getReference("messages/$chatId")
            .orderByChild("timestamp")
            .limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { child ->
                    child.getValue(Message::class.java)?.copy(id = child.key ?: "")
                }
                trySend(messages)
            }
            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getCachedMessages(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId).map { list ->
            list.map {
                Message(
                    id = it.id,
                    chatId = it.chatId,
                    senderId = it.senderId,
                    content = it.content,
                    type = MessageType.valueOf(it.type),
                    mediaUrl = it.mediaUrl,
                    isRead = it.isRead,
                    timestamp = it.timestamp
                )
            }
        }
    }

    suspend fun sendMessage(chatId: String, message: Message) {
        val ref = database.getReference("messages/$chatId").push()
        val newMsg = message.copy(id = ref.key ?: "")
        ref.setValue(newMsg).await()

        // Update chat last message
        database.getReference("chats/$chatId").updateChildren(
            mapOf(
                "lastMessage" to message.content,
                "lastMessageTime" to message.timestamp,
                "lastMessageSenderId" to message.senderId
            )
        ).await()

        // Cache
        messageDao.insertMessage(newMsg.toEntity())
    }

    suspend fun markAsRead(chatId: String, myId: String) {
        messageDao.markMessagesRead(chatId, myId)
        database.getReference("chats/$chatId/unreadCount/$myId").setValue(0).await()
    }

    fun setTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        database.getReference("typing/$chatId/$userId").setValue(isTyping)
    }

    fun getTypingStatus(chatId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val ref = database.getReference("typing/$chatId")
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
        val ref = database.getReference("presence/$userId")
        ref.setValue(mapOf("isOnline" to isOnline, "lastSeen" to System.currentTimeMillis()))
        if (isOnline) {
            ref.onDisconnect().setValue(
                mapOf("isOnline" to false, "lastSeen" to System.currentTimeMillis())
            )
        }
    }

    suspend fun getOrCreateChat(myId: String, otherId: String): String {
        val chatId = listOf(myId, otherId).sorted().joinToString("_")
        val ref = database.getReference("chats/$chatId")
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.setValue(
                Chat(
                    id = chatId,
                    participants = listOf(myId, otherId)
                )
            ).await()
        }
        return chatId
    }

    private fun Message.toEntity() = CachedMessage(
        id = id, chatId = chatId, senderId = senderId,
        content = content, type = type.name, mediaUrl = mediaUrl,
        isRead = isRead, timestamp = timestamp
    )
}
