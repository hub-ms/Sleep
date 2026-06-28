package com.sleepytime.shared.ui.alarm

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.platform.AudioSystem
import com.sleepytime.shared.platform.MusicPlayer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

class AlarmViewModel(
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val player: MusicPlayer,
    private val audioSystem: AudioSystem
) : ScreenModel {
    private val _state = MutableStateFlow(AlarmContract.State())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AlarmContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<AlarmContract.Intent>(Channel.BUFFERED)

    private val isUserChangingVolume = MutableStateFlow(false)
    private var lastCycle: Int? = null

    init {
        _state.update {
            it.copy(
                systemVolume = audioSystem.getSystemAlarmVolume(),
                appVolume = _state.value.appVolume
            )
        }
        audioSystem.observeVolumeChanges { systemVolume ->
            if (isUserChangingVolume.value) return@observeVolumeChanges

            val currentAppVolume = _state.value.appVolume
            if (currentAppVolume != systemVolume) {
                _state.update { it.copy(systemVolume = systemVolume) }
                player.setVolume(
                    systemVolume * currentAppVolume
                )
            }
        }
        screenModelScope.launch {
            _intentChannel.receiveAsFlow().collect { intent ->
                processIntent(intent)
            }
        }
    }

    fun sendIntent(intent: AlarmContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private suspend fun processIntent(intent: AlarmContract.Intent) {
        when (intent) {
            is AlarmContract.Intent.ToggleAlarm -> {
                _state.update {
                    it.copy(isAlarmEnabled = !it.isAlarmEnabled)
                }
            }

            is AlarmContract.Intent.ChangeAlarmHour -> {
                _state.update {
                    it.copy(alarmHour = intent.hour)
                }
                sleepSettingsRepository.setWakeUpTime(
                    intent.hour, _state.value.alarmMinute
                )
            }

            is AlarmContract.Intent.ChangeAlarmMinute -> {
                val minute = intent.minute
                val globalIndex = intent.globalIndex

                val newCycle = globalIndex / 12
                val prevCycle = lastCycle

                var newHour = _state.value.alarmHour

                if (prevCycle != null) {
                    val diff = newCycle - prevCycle

                    if (diff != 0) {
                        newHour = (newHour + diff + 24) % 24
                    }
                }

                lastCycle = newCycle

                _state.update {
                    it.copy(
                        alarmHour = newHour, alarmMinute = minute
                    )
                }
                sleepSettingsRepository.setWakeUpTime(
                    newHour, minute
                )
            }

            is AlarmContract.Intent.SetWakeUpTime -> {
                _state.update { it.copy(wakeUpTime = intent.time) }
                sleepSettingsRepository.setWakeUpTime(intent.time.hour, intent.time.minute)
            }


            is AlarmContract.Intent.SelectAlarmSound -> {
                _state.update { it.copy(selectedAlarmSound = intent.sound.titleRes) }
                player.stop()
            }

            is AlarmContract.Intent.ChangeVolume -> {
                onUserVolumeChange(intent.volume)
            }

            is AlarmContract.Intent.ToggleVibration -> {
                _state.update { it.copy(isVibrationEnabled = !it.isVibrationEnabled) }
            }

            is AlarmContract.Intent.ToggleSmartAlarm -> {
                _state.update { it.copy(isSmartAlarmEnabled = !it.isSmartAlarmEnabled) }
            }

            is AlarmContract.Intent.SelectSmartAlarmRange -> {
                _state.update { it.copy(selectedSmartAlarmRange = intent.range) }
            }

            is AlarmContract.Intent.ToggleGradualVolume -> {
                _state.update { it.copy(isGradualVolumeEnabled = !it.isGradualVolumeEnabled) }
            }

            is AlarmContract.Intent.ToggleAutoTracking -> {
                _state.update { it.copy(isAutoTrackingEnabled = !it.isAutoTrackingEnabled) }
            }

            is AlarmContract.Intent.SelectSleepTrackingMode -> onSelectSleepTrackingMode(intent.mode)
            is AlarmContract.Intent.AutoTrackingTimeChanged -> {
                updateAutoTrackingTime(intent.time)
                _state.update {
                    it.copy(autoTrackingTime = intent.time)
                }
            }

            is AlarmContract.Intent.SaveButtonClicked -> {
                player.stop()
                _effect.emit(AlarmContract.Effect.NavigateToHome)
            }
        }
    }

    fun onSelectSleepTrackingMode(mode: SleepTrackingMode) {
        _state.update { state ->
            val current = state.selectedSleepTrackingModes.toMutableSet()

            when (mode) {
                SleepTrackingMode.AUTO_PHONE -> {
                    if (current.contains(SleepTrackingMode.AUTO_PHONE)) {
                        if (current.contains(SleepTrackingMode.AUTO_WATCH)) current.remove(
                            SleepTrackingMode.AUTO_PHONE
                        )
                    } else current.add(SleepTrackingMode.AUTO_PHONE)
                }

                SleepTrackingMode.AUTO_WATCH -> {
                    if (current.contains(SleepTrackingMode.AUTO_WATCH)) {
                        if (current.contains(SleepTrackingMode.AUTO_PHONE)) current.remove(
                            SleepTrackingMode.AUTO_WATCH
                        )
                    } else current.add(SleepTrackingMode.AUTO_WATCH)
                }
            }

            val normalized = when {
                current.contains(SleepTrackingMode.AUTO_PHONE) && current.contains(SleepTrackingMode.AUTO_WATCH) -> {
                    setOf(
                        SleepTrackingMode.AUTO_PHONE, SleepTrackingMode.AUTO_WATCH
                    )
                }

                current.contains(SleepTrackingMode.AUTO_PHONE) -> {
                    setOf(SleepTrackingMode.AUTO_PHONE)
                }

                current.contains(SleepTrackingMode.AUTO_WATCH) -> {
                    setOf(SleepTrackingMode.AUTO_WATCH)
                }

                else -> setOf(SleepTrackingMode.AUTO_PHONE)
            }

            state.copy(
                selectedSleepTrackingModes = normalized
            )
        }
    }

    private fun updateAutoTrackingTime(time: LocalTime) {
        _state.update {
            it.copy(autoTrackingTime = time)
        }
    }

    fun onUserVolumeChange(volume: Float) {
        isUserChangingVolume.value = true

        _state.update { it.copy(appVolume = volume) }

        applyVolume()

        screenModelScope.launch {
            delay(300)
            isUserChangingVolume.value = false
        }
    }

    private fun applyVolume() {
        val system = _state.value.systemVolume
        val app = _state.value.appVolume

        val final = system * app

        player.setVolume(final)

        syncSystemVolumeIfNeeded(system)
    }

    private fun syncSystemVolumeIfNeeded(systemVolume: Float) {
        audioSystem.setSystemAlarmVolume(systemVolume)
    }
    override fun onDispose() {
        audioSystem.unregisterVolumeObserver ()
        player.stop()
        super.onDispose()
    }
}
