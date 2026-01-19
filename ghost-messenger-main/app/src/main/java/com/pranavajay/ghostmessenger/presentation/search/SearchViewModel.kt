package com.pranavajay.ghostmessenger.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavajay.ghostmessenger.data.models.Chat
import com.pranavajay.ghostmessenger.data.models.Message
import com.pranavajay.ghostmessenger.data.models.User
import com.pranavajay.ghostmessenger.data.repository.ChatRepository
import com.pranavajay.ghostmessenger.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val isLoading: Boolean = false,
    val messages: List<Message> = emptyList(),
    val chats: List<Chat> = emptyList(),
    val users: List<User> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()
    
    fun search(query: String) {
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true)
            
            when (val result = chatRepository.globalSearch(query)) {
                is Result.Success -> {
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        messages = result.data.messages,
                        chats = result.data.chats,
                        users = result.data.users,
                        error = null
                    )
                }
                is Result.Error -> {
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun searchMessages(query: String, chatId: String? = null) {
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true)
            
            when (val result = chatRepository.searchMessages(query, chatId)) {
                is Result.Success -> {
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        messages = result.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
}
