package com.soundsleeper.app.ui.alarm

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.domain.repository.SleepSessionRepository
import com.soundsleeper.app.domain.repository.SleepSettingRepository
import com.soundsleeper.app.platform.AudioSystem
import com.soundsleeper.app.platform.BedtimeReminderScheduler
import com.soundsleeper.app.platform.MusicPlayer
import com.soundsleeper.app.platform.SoundType
import com.soundsleeper.app.platform.TrackingManager
import com.soundsleeper.app.util.DateTimeUtil.toLocalDateTime
import com.soundsleeper.app.util.PreferencesKeys.Settings.KEY_REMINDER_ENABLED
import com.soundsleeper.app.util.PreferencesKeys.Settings.KEY_REMINDER_HOUR
import com.soundsleeper.app.util.PreferencesKeys.Settings.KEY_REMINDER_MINUTE
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
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

class AlarmViewModel(
    private val sleepSessionRepository: SleepSessionRepository,
    private val sleepSettingRepository: SleepSettingRepository,
    private val player: MusicPlayer,
    private val audioSystem: AudioSystem,
    private val trackingManager: TrackingManager,
    private val settings: ObservableSettings,
    private val reminderScheduler: BedtimeReminderScheduler,
)  : AppScopedScreenModel() {
    private val _state = MutableStateFlow(AlarmContract.State())
    val state = _state.asStateFlow()

    // 버퍼가 없으면 수집자가 없는 순간의 emit 이 영원히 멈춘다. 화면 전환 중에는
    // 수집자가 잠깐 사라지므로 한 칸은 남겨 둔다.
    private val _effect = MutableSharedFlow<AlarmContract.Effect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<AlarmContract.Intent>(Channel.BUFFERED)

    private val isUserChangingVolume = MutableStateFlow(false)
    private var lastCycle: Int? = null

    init {
        Napier.i("AlarmViewModel initialized")
        modelScope.launch {
            val savedSleepSetting = sleepSettingRepository.observeSettings().first()
            _state.update {
                it.copy(
                    alarmHour = savedSleepSetting.alarm.hour,
                    alarmMinute = savedSleepSetting.alarm.minute,
                    isAlarmEnabled = savedSleepSetting.alarm.isEnabled,
                    isVibrationEnabled = savedSleepSetting.alarm.isVibrationEnabled,
                    isSmartAlarmEnabled = savedSleepSetting.alarm.isSmartAlarmEnabled,
                    selectedSmartAlarmRange = savedSleepSetting.alarm.smartAlarmRange,
                    appVolume = savedSleepSetting.alarm.sound.volume,
                    systemVolume = audioSystem.getSystemAlarmVolume()
                )
            }
        }
        run {
            val isReminderEnabled = settings.getBoolean(KEY_REMINDER_ENABLED, true)
            val reminderHour = settings.getInt(KEY_REMINDER_HOUR, 23)
            val reminderMinute = settings.getInt(KEY_REMINDER_MINUTE, 0)
            _state.update {
                it.copy(
                    isReminderEnabled = isReminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                )
            }
            if (isReminderEnabled) {
                reminderScheduler.schedule(reminderHour, reminderMinute)
            } else {
                reminderScheduler.cancel()
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
        modelScope.launch {
            _intentChannel.receiveAsFlow().collect { intent ->
                Napier.d(tag = "AlarmVM", message = "Received intent: $intent")
                processIntent(intent)
            }
        }
    }

    fun sendIntent(intent: AlarmContract.Intent) {
        modelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private suspend fun processIntent(intent: AlarmContract.Intent) {
        when (intent) {
            is AlarmContract.Intent.ToggleAlarm -> {
                val newStatus = !_state.value.isAlarmEnabled
                _state.update { it.copy(isAlarmEnabled = newStatus) }
                sleepSettingRepository.updateAlarmEnabled(newStatus)
            }
            is AlarmContract.Intent.ChangeAlarmHour -> {
                _state.update {
                    it.copy(alarmHour = intent.hour)
                }
                lastCycle = null
                updateAlarmTime(
                    hour = intent.hour,
                    minute = _state.value.alarmMinute
                )
            }
            is AlarmContract.Intent.ChangeAlarmMinute -> {
                val finalHour = calculateHour(
                    currentHour = _state.value.alarmHour,
                    globalIndex = intent.globalIndex
                )
                _state.update {
                    it.copy(
                        alarmHour = finalHour,
                        alarmMinute = intent.minute
                    )
                }
                updateAlarmTime(
                    hour = finalHour,
                    minute = intent.minute
                )
            }
            is AlarmContract.Intent.ChangeReminderHour -> {
                _state.update {
                    it.copy(reminderHour = intent.hour)
                }
                lastCycle = null
                updateReminderTime(
                    hour = intent.hour,
                    minute = _state.value.reminderMinute
                )
            }
            is AlarmContract.Intent.ChangeReminderMinute -> {
                val finalHour = calculateHour(
                    currentHour = _state.value.reminderHour,
                    globalIndex = intent.globalIndex
                )
                _state.update {
                    it.copy(
                        reminderHour = finalHour,
                        reminderMinute = intent.minute
                    )
                }
                updateReminderTime(
                    hour = finalHour,
                    minute = intent.minute
                )
            }

            is AlarmContract.Intent.SelectAlarmSound -> {
                _state.update {
                    it.copy(
                        selectedAlarmSound = intent.sound,
                        isAlarmPreviewPlaying = true
                    )
                }
                sleepSettingRepository.updateSound(intent.sound)

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
                val trackingSnapshot = trackingManager.trackingState.value
                val currentSessionId = trackingSnapshot.sessionId ?: ""
                val currentMusicName = trackingSnapshot.musicName

                // 예전에는 이 클래스의 _trackingState(어디서도 쓰기가 일어나지 않던 죽은 필드)를 읽어
                // 항상 기본값 23:00~07:00, 즉 8시간 고정으로 계산했다. 그 탓에 아래 단축 수면 분기가
                // 한 번도 발동하지 못하고 짧게 끝난 세션까지 리포트로 보냈다.
                // 측정 화면과 같은 기준인 실제 경과 시간을 쓴다.
                val sleepDurationMinutes = trackingSnapshot.elapsedMillis / (1000 * 60)

                trackingManager.finish(currentSessionId, currentMusicName)

                _state.update {
                    it.copy(isAlarmPlaying = false)
                }

                if(sleepDurationMinutes<30) {
                    _effect.emit(AlarmContract.Effect.NavigateToHome)
                    return
                }
                val finishedState = withTimeoutOrNull(10_000L.milliseconds) {
                    trackingManager.trackingState.first { it.isFinished }
                }
                // 🐛 버그 수정: 분석 결과와 상관없이 finish() 호출 전 sessionId를 그대로 넘겨서,
                // 분석이 실패해 DB에 저장되지 않은 세션으로 이동한 뒤 리포트 화면이 10초간
                // 재시도만 하다 최신 세션으로 대체되는 낭비가 있었다. 분석이 끝난 뒤의 실제
                // 결과(finishedSessionId, 실패 시 null)를 쓴다. 타임아웃으로 끝내 결과를 못
                // 받았을 때만 원래 sessionId로 최선 추정한다.
                val resolvedSessionId = if (finishedState != null) finishedState.finishedSessionId
                    else currentSessionId.ifBlank { null }
                _effect.emit(AlarmContract.Effect.NavigateToReport(resolvedSessionId))
            }

            is AlarmContract.Intent.ChangeVolume -> {
                onUserVolumeChange(intent.volume)
            }

            is AlarmContract.Intent.ToggleVibration -> {
                val newStatus = !_state.value.isVibrationEnabled
                _state.update { it.copy(isVibrationEnabled = newStatus) }
                sleepSettingRepository.updateVibrationEnabled(newStatus)
            }

            is AlarmContract.Intent.ToggleSmartAlarm -> {
                val newStatus = !_state.value.isSmartAlarmEnabled
                _state.update { it.copy(isSmartAlarmEnabled = newStatus) }
                sleepSettingRepository.updateSmartAlarmEnabled(newStatus)
            }

            is AlarmContract.Intent.SelectSmartAlarmRange -> {
                _state.update { it.copy(selectedSmartAlarmRange = intent.range) }
                sleepSettingRepository.updateSmartAlarmRange(intent.range)
            }
            is AlarmContract.Intent.ToggleRecommend -> {
                val newStatus = !_state.value.isRecommendEnabled
                _state.update { it.copy(isRecommendEnabled = newStatus) }
                if (newStatus) recommendWakeTime(sleepSessionRepository)
            }
            is AlarmContract.Intent.ToggleSleepReminder -> {
                settings.putBoolean(KEY_REMINDER_ENABLED, intent.enabled)
                _state.update { it.copy(isReminderEnabled = intent.enabled) }
                if (intent.enabled) {
                    reminderScheduler.schedule(_state.value.reminderHour, _state.value.reminderMinute)
                } else {
                    reminderScheduler.cancel()
                }
            }
        }
    }
    private fun calculateHour(
        currentHour: Int,
        globalIndex: Int
    ): Int {
        val newCycle = globalIndex / 12
        val prevCycle = lastCycle
        lastCycle = newCycle
        if (prevCycle == null) return currentHour
        val diff = newCycle - prevCycle
        return (currentHour + diff + 24) % 24
    }
    private suspend fun updateAlarmTime(
        hour: Int,
        minute: Int
    ) {
        sleepSettingRepository.updateAlarmTime(hour, minute)
        if (trackingManager.trackingState.value.isTracking) trackingManager.updateEndTime(hour, minute)
    }
    private suspend fun updateReminderTime(
        hour: Int,
        minute: Int
    ) {
        sleepSettingRepository.updateReminderTime(hour, minute)
        if (_state.value.isReminderEnabled) reminderScheduler.schedule(hour, minute)
    }
    private fun recommendWakeTime(sleepSessionRepository: SleepSessionRepository) {
        modelScope.launch {
            val sessions = sleepSessionRepository.getRecentSessions(14)
            if (sessions.isEmpty()) return@launch

            val avgWakeTime = sessions.map { it.wakeTime }.averageTime()
            val sleepDurationMinutes = sessions.map { it.duration.sleepLatencyMinutes + it.duration.awakeMinutes + it.duration.lightMinutes + it.duration.deepMinutes + it.duration.remMinutes }

            val avgSleepDuration = sleepDurationMinutes.average().toInt()

            val recentWakeTimes = sessions.takeLast(3).map { it.wakeTime }
            val weightedWakeTime = weightedAverage(avgWakeTime, recentWakeTimes)

            val recommendedWakeTime = when {
                avgSleepDuration < 420 -> weightedWakeTime.minute.plus(30).toLocalDateTime()
                avgSleepDuration > 540 -> weightedWakeTime.minute.minus(30).toLocalDateTime()
                else -> weightedWakeTime
            }

            _state.update {
                it.copy(
                    alarmHour = recommendedWakeTime.hour,
                    alarmMinute = recommendedWakeTime.minute
                )
            }
        }
    }

    // 평균 시간 계산
    private fun List<LocalDateTime>.averageTime(): LocalDateTime {
        val avgHour = this.map { it.hour }.average().toInt()
        val avgMinute = this.map { it.minute }.average().toInt()
        return LocalDateTime(
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            LocalTime(avgHour, avgMinute)
        )
    }

    // 최근 데이터 가중치 반영
    private fun weightedAverage(base: LocalDateTime, recent: List<LocalDateTime>): LocalDateTime {
        val avgHour = ((base.hour * 0.7) + (recent.map { it.hour }.average() * 0.3)).toInt()
        val avgMinute = ((base.minute * 0.7) + (recent.map { it.minute }.average() * 0.3)).toInt()
        return LocalDateTime(
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            LocalTime(avgHour, avgMinute)
        )
    }



    fun onUserVolumeChange(volume: Float) {
        isUserChangingVolume.value = true
        _state.update { it.copy(appVolume = volume) }
        applyVolume()
        modelScope.launch {
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
        player.stop()
        super.onDispose()
    }
}
