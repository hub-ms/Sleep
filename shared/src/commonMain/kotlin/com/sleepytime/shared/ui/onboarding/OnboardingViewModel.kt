package com.sleepytime.shared.ui.onboarding

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.enum_.OnboardingSelectionMode
import com.sleepytime.shared.enum_.PermissionType
import com.sleepytime.shared.platform.PermissionState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel: ScreenModel {

    private val _state = MutableStateFlow(OnboardingContract.State())
    val state: StateFlow<OnboardingContract.State> = _state.asStateFlow()

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState = _permissionState.asStateFlow()

    private val _effect = MutableSharedFlow<OnboardingContract.Effect>()
    val effect: SharedFlow<OnboardingContract.Effect> = _effect.asSharedFlow()

    private val _intentChannel = Channel<OnboardingContract.Intent>(Channel.BUFFERED)

    init {
        screenModelScope.launch {
            for (intent in _intentChannel) {
                processIntent(intent)
            }
        }
    }

    fun sendIntent(intent: OnboardingContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    val questions = listOf(
        Question(
            title = "수면과 관련해서 어떤 문제가 있나요?",
            options = listOf(
                "잠이 안 와요",
                "자다가 자주 깨요",
                "자고 일어나도 피곤해요",
                "자는 시간이 일정하지 않아요",
                "잠을 충분히 못 자요",
            ),
            selectionMode = OnboardingSelectionMode.MULTI
        ),
        Question(
            title = "잠잘 때 주변 환경은 어떤가요?",
            options = listOf(
                "밝아요",
                "시끄러워요",
                "덥거나 추워요",
                "공기가 답답해요",
                "평안해요"
            ),
            selectionMode = OnboardingSelectionMode.SINGLE
        ),
        Question(
            title = "자기 전에 보통 무엇을 하나요?",
            options = listOf(
                "스마트폰을 사용해요",
                "영상이나 TV를 봐요",
                "늦게까지 깨어 있어요",
                "힘들기 전에 다른 활동을 해요",
                "바로 잠자리에 들어요"
            ),
            selectionMode = OnboardingSelectionMode.SINGLE
        )
    )
    val totalSteps = 3 // LoginPage is the final step (step 2)

    private suspend fun processIntent(intent: OnboardingContract.Intent) {
        when (intent) {
            is OnboardingContract.Intent.NextStep -> {
                val next = (_state.value.step + 1).coerceAtMost(2)
                Napier.d(tag = "OnboardingVM", message = "Next step: $next")
                _state.update { it.copy(step = next) }
            }
            is OnboardingContract.Intent.PermissionGranted -> {
                _state.update { it.copy(step = 2) }
            }
            is OnboardingContract.Intent.PermissionDenied -> {
                _state.update { it.copy(step = 2) }
            }
        }
    }

    fun updatePermission(type: PermissionType, granted: Boolean) {
        Napier.d(tag = "OnboardingVM", message = "Update permission: $type = $granted")
        _permissionState.update {
            when (type) {
                PermissionType.AUDIO -> it.copy(audio = granted)
                PermissionType.NOTIFICATION -> it.copy(notification = granted)
                PermissionType.ACTIVITY_RECOGNITION -> it.copy(activity = granted)
                PermissionType.BATTERY_OPTIMIZATION -> it.copy(batteryOptimizationIgnored = granted)
            }
        }
    }
}
