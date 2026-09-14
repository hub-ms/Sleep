package com.sleepytime.shared.ui.alarm

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.platform.AudioSystem
import com.sleepytime.shared.platform.MusicPlayer
import com.sleepytime.shared.platform.SoundType
import com.sleepytime.shared.platform.TrackingManager
import com.sleepytime.shared.ui.tracking.TrackingContract
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.milliseconds

class AlarmViewModel(
    private val authRepository: AuthRepository,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val player: MusicPlayer,
    private val audioSystem: AudioSystem,
    private val trackingManager: TrackingManager
) : ScreenModel {
    private val _state = MutableStateFlow(AlarmContract.State())
    val state = _state.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingContract.State())
    val trackingState = _trackingState.asStateFlow()


    private val _effect = MutableSharedFlow<AlarmContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<AlarmContract.Intent>(Channel.BUFFERED)

    private val isUserChangingVolume = MutableStateFlow(false)
    private var lastCycle: Int? = null

    init {
        Napier.i("AlarmViewModel initialized")
        screenModelScope.launch {
            val savedAlarm = sleepSettingsRepository.observeSettings().first()
            _state.update {
                it.copy(
                    alarmHour = savedAlarm.hour,
                    alarmMinute = savedAlarm.minute,
                    isAlarmEnabled = savedAlarm.isEnabled,
                    isVibrationEnabled = savedAlarm.isVibrationEnabled,
                    isSmartAlarmEnabled = savedAlarm.isSmartAlarmEnabled,
                    selectedSmartAlarmRange = savedAlarm.smartAlarmRange,
                    appVolume = savedAlarm.sound.volume,
                    systemVolume = audioSystem.getSystemAlarmVolume()
                )
            }
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
                Napier.d(tag = "AlarmVM", message = "Received intent: $intent")
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
                val newStatus = !_state.value.isAlarmEnabled
                _state.update { it.copy(isAlarmEnabled = newStatus) }
                sleepSettingsRepository.setAlarmEnabled(newStatus)
            }

            is AlarmContract.Intent.ChangeAlarmHour -> {
                _state.update { it.copy(alarmHour = intent.hour) }
                lastCycle = null
                val currentMinute = _state.value.alarmMinute
                
                sleepSettingsRepository.setWakeUpTime(intent.hour, currentMinute)
                
                // 💡 측정 중이라면 진행 중인 세션의 기상 시각도 즉시 동기화
                if (trackingManager.trackingState.value.isTracking) {
                    trackingManager.updateEndTime(intent.hour, currentMinute)
                }
            }

            is AlarmContract.Intent.ChangeAlarmMinute -> {
                val newCycle = intent.globalIndex / 12
                val prevCycle = lastCycle
                lastCycle = newCycle

                var finalHour = _state.value.alarmHour
                val finalMinute = intent.minute

                if (prevCycle != null && newCycle != prevCycle) {
                    val diff = newCycle - prevCycle
                    finalHour = (finalHour + diff + 24) % 24
                }

                _state.update { it.copy(alarmHour = finalHour, alarmMinute = finalMinute) }

                sleepSettingsRepository.setWakeUpTime(finalHour, finalMinute)
                
                // 💡 측정 중이라면 진행 중인 세션의 기상 시각도 즉시 동기화
                if (trackingManager.trackingState.value.isTracking) {
                    trackingManager.updateEndTime(finalHour, finalMinute)
                }
            }

            is AlarmContract.Intent.SelectAlarmSound -> {
                _state.update {
                    it.copy(
                        selectedAlarmSound = intent.sound,
                        isAlarmPreviewPlaying = true
                    )
                }
                sleepSettingsRepository.setSelectedMusic(intent.sound.id)

                val finalVolume = _state.value.systemVolume * _state.value.appVolume

                player.stop()
                player.setVolume(finalVolume)
                player.play(intent.sound.id, type = SoundType.ALARM)
            }

            is AlarmContract.Intent.StopAlarmPreview -> {
                player.stop()
                _state.update { it.copy(isAlarmPreviewPlaying = false) }
            }

            is AlarmContract.Intent.StopAlarm -> {
                val currentSessionId = trackingManager.trackingState.value.sessionId ?: ""
                val currentMusicTitle = trackingManager.trackingState.value.musicTitle

                val trackingStartInstant = _trackingState.value.trackingStartTime.toInstant(TimeZone.currentSystemDefault())
                val trackingEndInstant = _trackingState.value.trackingEndTime.toInstant(TimeZone.currentSystemDefault())

                val sleepDurationMillis = (trackingEndInstant - trackingStartInstant).inWholeMilliseconds
                val sleepDurationMinutes = sleepDurationMillis / (1000 * 60)


                
                trackingManager.finish(currentSessionId, currentMusicTitle)

                _state.update {
                    it.copy(isAlarmPlaying = false)
                }

                if(sleepDurationMinutes<30) {
                    _effect.emit(AlarmContract.Effect.NavigateToHome)
                    return
                }

                // 🐛 버그 수정: finish()는 포그라운드 서비스에 인텐트만 보내고 즉시 반환되는
                // fire-and-forget 호출이라, 세션 분석/저장(analyzeAndSave)이 끝나기 전에 리포트
                // 화면으로 이동하면 방금 잰 수면 데이터가 아직 저장되지 않아 0값으로 보였습니다.
                // isFinished가 true가 될 때까지(최대 10초) 기다린 뒤 리포트로 이동합니다.
                withTimeoutOrNull(10_000L) {
                    trackingManager.trackingState.first { it.isFinished }
                }
                _effect.emit(AlarmContract.Effect.NavigateToReport(currentSessionId))
            }

            is AlarmContract.Intent.ChangeVolume -> {
                onUserVolumeChange(intent.volume)
                sleepSettingsRepository.setVolume(intent.volume)
            }

            is AlarmContract.Intent.ToggleVibration -> {
                val newStatus = !_state.value.isVibrationEnabled
                _state.update { it.copy(isVibrationEnabled = newStatus) }
                sleepSettingsRepository.setVibrationEnabled(newStatus)
            }

            is AlarmContract.Intent.ToggleSmartAlarm -> {
                val newStatus = !_state.value.isSmartAlarmEnabled
                _state.update { it.copy(isSmartAlarmEnabled = newStatus) }
                sleepSettingsRepository.setSmartAlarmEnabled(newStatus)
            }

            is AlarmContract.Intent.SelectSmartAlarmRange -> {
                _state.update { it.copy(selectedSmartAlarmRange = intent.range) }
                sleepSettingsRepository.setSmartAlarmRange(intent.range)
            }
        }
    }

    fun onUserVolumeChange(volume: Float) {
        isUserChangingVolume.value = true
        _state.update { it.copy(appVolume = volume) }
        applyVolume()
        screenModelScope.launch {
            delay(300.milliseconds)
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
        audioSystem.unregisterVolumeObserver()
        player.stop()
        super.onDispose()
    }
}
