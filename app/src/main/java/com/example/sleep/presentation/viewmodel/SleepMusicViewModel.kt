package com.example.sleep.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleep.SleepMusicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SleepMusicViewModel : ViewModel() {
    private val _selectedIndex = MutableStateFlow(-1)
    private val _currentIndex = MutableStateFlow(0)
    private val _isLoop = MutableStateFlow(false)
    private val _isTimer = MutableStateFlow(false)
    private val _sleepMusicList = MutableStateFlow(listOf<SleepMusicItem>())

    val selectedIndex = _selectedIndex.asStateFlow()
    val currentIndex = _currentIndex.asStateFlow()
    val isLoop = _isLoop.asStateFlow()
    val isTimer = _isTimer.asStateFlow()
    val sleepMusicList = _sleepMusicList.asStateFlow()
//    init {
//        loadSleepMusic()
//    }
//
//    private fun loadSleepMusic() {
//        _sleepMusicList.value = listOf(
//            SleepMusicItem(
//                title = "빗소리",
//                description = "수면에 도움이 되는 빗소리",
//                imageRes = R.drawable.rain,
//                musicRes = R.raw.rain_sound,
//                duration = 180.minutes.inWholeMilliseconds,
//                isLoop = false,
//                isTimer = false
//            ),
//            SleepMusicItem(
//                title = "숲속의 소리",
//                description = "수면에 도움이 되는 숲속의 소리",
//                imageRes = R.drawable.forest,
//                musicRes = R.raw.forest_sound,
//                duration = 180.minutes.inWholeMilliseconds,
//                isLoop = false,
//                isTimer = false
//            ),
//            SleepMusicItem(
//                title = "델타파 3Hz",
//                description = "수면에 도움이 되는 델타파 3Hz",
//                imageRes = R.drawable.forest,
//                musicRes = R.raw.delta_wave_3hz,
//                duration = 180.minutes.inWholeMilliseconds,
//                isLoop = false,
//                isTimer = false
//            ),
//            SleepMusicItem(
//                title = "델타파 3Hz",
//                description = "수면에 도움이 되는 델타파 3Hz",
//                imageRes = R.drawable.forest,
//                musicRes = R.raw.delta_wave_3hz,
//                duration = 180.minutes.inWholeMilliseconds,
//                isLoop = false,
//                isTimer = false
//            ),
//            SleepMusicItem(
//                title = "델타파 3Hz",
//                description = "수면에 도움이 되는 델타파 3Hz",
//                imageRes = R.drawable.forest,
//                musicRes = R.raw.delta_wave_3hz,
//                duration = 180.minutes.inWholeMilliseconds,
//                isLoop = false,
//                isTimer = false
//            ),
//            SleepMusicItem(
//                title = "델타파 3Hz",
//                description = "수면에 도움이 되는 델타파 3Hz",
//                imageRes = R.drawable.forest,
//                musicRes = R.raw.delta_wave_3hz,
//                duration = 180.minutes.inWholeMilliseconds,
//                isLoop = false,
//                isTimer = false
//            ),
//            SleepMusicItem(
//                title = "델타파 3Hz",
//                description = "수면에 도움이 되는 델타파 3Hz",
//                imageRes = R.drawable.forest,
//                musicRes = R.raw.delta_wave_3hz,
//                duration = 180.minutes.inWholeMilliseconds,
//                isLoop = false,
//                isTimer = false
//            ),
//        )
//    }

    val selectedMusic = combine(_selectedIndex, _sleepMusicList) { index, list ->
        if (list.isNotEmpty() && index in list.indices)
            list[index]
        else if (list.isNotEmpty())
            list.first()
        else
            null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Companion.Eagerly,
        initialValue = null
    )

    fun selectMusic(index: Int) {
        _selectedIndex.value = index
    }

    fun clearSelection() {
        _selectedIndex.value = -1
        _isLoop.value = false
        _isTimer.value = false
    }

    fun toggleLoop() {
        _isLoop.value = !_isLoop.value
    }

    fun toggleTimer() {
        _isTimer.value = !_isTimer.value
    }
}