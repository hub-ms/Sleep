package com.example.sleep.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleep.presentation.contract.TrackingContract
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
): ViewModel() {
    private val _state = MutableStateFlow(TrackingContract.TrackingState())
    val state: StateFlow<TrackingContract.TrackingState> = _state

    private var timerJob: Job? = null

    init {
        startTimer()
    }

    fun onIntent(intent: TrackingContract.TrackingIntent) {
        when(intent) {
            TrackingContract.TrackingIntent.StartTracking -> startTracking()
            TrackingContract.TrackingIntent.StopTracking -> stopTracking()
            TrackingContract.TrackingIntent.FinishTracking -> finishTracking()
            TrackingContract.TrackingIntent.SelectMusic -> selectMusic()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                updateTime()
            }
        }
    }

    private fun updateTime() {
        val current=_state.value
        if(current.remainingMinute == 0) {
            if(current.remainingHour == 0) {
                _state.update { it.copy(isFinished = true) }
            } else {
                _state.update {
                    it.copy(
                        remainingHour = it.remainingHour - 1,
                        remainingMinute = 59
                    )
                }
            }
        } else {
            _state.update {
                it.copy(
                    remainingMinute = it.remainingMinute - 1
                )
            }
        }
    }

    private fun startTracking() {
        _state.update { it.copy(isTracking = true) }
    }

    private fun stopTracking() {
        _state.update { it.copy(isTracking = false) }
    }

    private fun finishTracking() {
        _state.update { it.copy(isFinished = true) }
    }

    private fun selectMusic() {
        _state.update {
            it.copy(musicTitle = it.musicTitle)
        }
        _state.update {
            it.copy(musicImageRes = it.musicImageRes)
        }
    }
}