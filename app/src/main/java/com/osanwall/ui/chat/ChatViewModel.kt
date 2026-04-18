package com.osanwall.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osanwall.data.model.*
import com.osanwall.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val myId: String = authRepository.currentUserId ?: ""

    val chats: StateFlow<List<Chat>> = chatRepository.getChats(myId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingUsers = _typingUsers.asStateFlow()

    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            chatRepository.getMessages(chatId).collect { msgs ->
                _messages.value = msgs
            }
        }
        viewModelScope.launch {
            chatRepository.getTypingStatus(chatId).collect { typing ->
                _typingUsers.value = typing
            }
        }
    }

    fun sendMessage(chatId: String, content: String, type: MessageType = MessageType.TEXT) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val msg = Message(
                chatId = chatId,
                senderId = myId,
                content = content,
                type = type,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.sendMessage(chatId, msg)
        }
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        chatRepository.setTypingStatus(chatId, myId, isTyping)
    }

    fun markAsRead(chatId: String) {
        viewModelScope.launch { chatRepository.markAsRead(chatId, myId) }
    }

    fun setOnline(isOnline: Boolean) = chatRepository.setOnlineStatus(myId, isOnline)

    suspend fun getOrCreateChat(otherId: String): String =
        chatRepository.getOrCreateChat(myId, otherId)
}
