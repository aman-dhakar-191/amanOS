package com.amanOS.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amanOS.messaging.data.MessagingRepository
import com.amanOS.messaging.data.SmsMessageEntity
import com.amanOS.messaging.data.SmsThreadEntity
import com.amanOS.messaging.sms.SmsSender
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MessagingViewModel @Inject constructor(
    private val repository: MessagingRepository,
    private val smsSender: SmsSender
) : ViewModel() {

    val threads: StateFlow<List<SmsThreadEntity>> = repository.getThreads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeThreadId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<SmsMessageEntity>> = activeThreadId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openThread(threadId: Long) {
        activeThreadId.value = threadId
    }

    fun sendMessage(to: String, body: String) {
        if (to.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            smsSender.send(to, body)
        }
    }

    fun markRead(threadId: Long) {
        viewModelScope.launch {
            repository.markRead(threadId)
        }
    }
}
