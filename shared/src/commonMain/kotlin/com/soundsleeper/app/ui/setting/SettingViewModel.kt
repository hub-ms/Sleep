package com.soundsleeper.app.ui.setting

import com.soundsleeper.app.AppConfig
import com.soundsleeper.app.domain.repository.VersionRepository
import com.soundsleeper.app.ui.common.AppScopedScreenModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingViewModel(
    private val appConfig: AppConfig,
    private val versionRepository: VersionRepository,
) : AppScopedScreenModel() {

    private val _state = MutableStateFlow(SettingContract.State(appConfig.versionName))
    val state = _state.asStateFlow()
    private val _effect = MutableSharedFlow<SettingContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<SettingContract.Intent>(Channel.BUFFERED)

    init {
        modelScope.launch {
            _intentChannel.receiveAsFlow().collect { intent ->
                processIntent(intent)
            }
        }
    }
    fun sendIntent(intent: SettingContract.Intent) {
        modelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private fun processIntent(intent: SettingContract.Intent) {
        when (intent) {
            SettingContract.Intent.CheckLatestVersion -> {
                checkLatestVersion()
            }
            SettingContract.Intent.ClickReview -> {
                modelScope.launch {
                    _effect.emit(
                        SettingContract.Effect.NavigateToReview
                    )
                }
            }
            SettingContract.Intent.ClickSubscribe -> {
                modelScope.launch {
                    _effect.emit(
                        SettingContract.Effect.NavigateToPaywall
                    )
                }
            }
            SettingContract.Intent.ClickManageSubscription -> {
                modelScope.launch {
                    _effect.emit(
                        SettingContract.Effect.NavigateToSubscriptionManage
                    )
                }
            }
            SettingContract.Intent.OpenStorePage -> {
                if (!_state.value.isLatestVersion) {
                    modelScope.launch {
                        _effect.emit(
                            SettingContract.Effect.NavigateToStorePage
                        )
                    }
                }
            }
        }
    }
    private fun checkLatestVersion() {
        modelScope.launch {
            _state.update {
                it.copy(isCheckingVersion = true)
            }

            val latestVersion = versionRepository.getLatestVersion()

            val isLatest =
                latestVersion == null ||
                        latestVersion == appConfig.versionName

            _state.update {
                it.copy(
                    latestVersion = latestVersion,
                    isLatestVersion = isLatest,
                    isCheckingVersion = false
                )
            }
        }
    }
}