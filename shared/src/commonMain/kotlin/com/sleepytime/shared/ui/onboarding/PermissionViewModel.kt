package com.sleepytime.shared.ui.onboarding

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.enum_.PermissionType
import com.sleepytime.shared.util.PreferencesKeys
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PermissionViewModel(
    private val settings: ObservableSettings
): ScreenModel {

    private val _state = MutableStateFlow(PermissionContract.State())
    val state: StateFlow<PermissionContract.State> = _state.asStateFlow()

    private val _intentChannel = Channel<PermissionContract.Intent>(Channel.BUFFERED)

    init {
        screenModelScope.launch {
            for (intent in _intentChannel) {
                processIntent(intent)
            }
        }
    }
    fun sendIntent(intent: PermissionContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }
    private fun processIntent(intent: PermissionContract.Intent) {
        when (intent) {
            is PermissionContract.Intent.PermissionGranted -> {
            }
            is PermissionContract.Intent.PermissionDenied -> {
            }
        }
    }

    fun updatePermission(type: PermissionType, granted: Boolean) {
        Napier.d(tag = "OnboardingVM", message = "Update permission: $type = $granted")
        _state.update {
            when (type) {
                PermissionType.AUDIO -> it.copy(audio = granted)
                PermissionType.NOTIFICATION -> it.copy(notification = granted)
                PermissionType.ACTIVITY_RECOGNITION -> it.copy(activity = granted)
                PermissionType.BATTERY_OPTIMIZATION -> it.copy(batteryOptimizationIgnored = granted)
            }
        }
    }

    // 최초실행 권한 온보딩을 완료 표시해서, 다음부터는 홈 진입 시 이 화면으로 다시 보내지 않습니다.
    fun markOnboardingDone() {
        settings.putBoolean(PreferencesKeys.App.PERMISSION_ONBOARDING_DONE, true)
    }
}
