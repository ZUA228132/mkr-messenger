package com.pioneer.messenger.ui.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelsViewModel @Inject constructor() : ViewModel() {
    
    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    init {
        loadReels()
    }
    
    private fun loadReels() {
        viewModelScope.launch {
            // TODO: Загрузка реальных видео с сервера
            _reels.value = listOf(
                Reel(
                    id = "1",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    thumbnailUrl = "https://picsum.photos/1080/1920?random=1",
                    authorId = "user1",
                    authorName = "limo_user",
                    authorAvatar = "https://picsum.photos/200?random=1",
                    description = "Крутое видео! 🔥 #limo #viral",
                    likes = 12500,
                    comments = 234,
                    shares = 89,
                    musicName = "Original Sound",
                    musicAuthor = "limo_user"
                ),
                Reel(
                    id = "2",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    thumbnailUrl = "https://picsum.photos/1080/1920?random=2",
                    authorId = "user2",
                    authorName = "cool_creator",
                    authorAvatar = "https://picsum.photos/200?random=2",
                    description = "Смотри что я нашёл! 😱",
                    likes = 45600,
                    comments = 567,
                    shares = 234,
                    musicName = "Trending Sound",
                    musicAuthor = "Artist Name"
                ),
                Reel(
                    id = "3",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    thumbnailUrl = "https://picsum.photos/1080/1920?random=3",
                    authorId = "user3",
                    authorName = "viral_content",
                    authorAvatar = "https://picsum.photos/200?random=3",
                    description = "Это просто огонь! 🚀 #trending",
                    likes = 89000,
                    comments = 1234,
                    shares = 456,
                    musicName = "Popular Track",
                    musicAuthor = "Music Artist"
                )
            )
        }
    }
    
    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index
    }
    
    fun toggleLike(reelId: String) {
        viewModelScope.launch {
            _reels.value = _reels.value.map { reel ->
                if (reel.id == reelId) {
                    reel.copy(
                        isLiked = !reel.isLiked,
                        likes = if (reel.isLiked) reel.likes - 1 else reel.likes + 1
                    )
                } else {
                    reel
                }
            }
        }
    }
    
    fun toggleSave(reelId: String) {
        viewModelScope.launch {
            _reels.value = _reels.value.map { reel ->
                if (reel.id == reelId) {
                    reel.copy(isSaved = !reel.isSaved)
                } else {
                    reel
                }
            }
        }
    }
    
    fun shareReel(reelId: String) {
        viewModelScope.launch {
            // TODO: Реализовать шаринг
            _reels.value = _reels.value.map { reel ->
                if (reel.id == reelId) {
                    reel.copy(shares = reel.shares + 1)
                } else {
                    reel
                }
            }
        }
    }
}
