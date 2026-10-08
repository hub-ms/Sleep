package com.soundsleeper.app.ui.onboarding

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.enum_.PermissionType
import com.soundsleeper.app.util.PreferencesKeys
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PermissionViewModel(
    private val settings: ObservableSettings
) : AppScopedScreenModel() {

    private val _state = MutableStateFlow(PermissionContract.State())
    val state: StateFlow<PermissionContract.State> = _state.asStateFlow()

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
    fun markOnboardingDone() {
        settings.putBoolean(PreferencesKeys.App.PERMISSION_ONBOARDING_DONE, true)
    }

    /** 수면 측정 안내 화면을 이미 본 적이 있는가. 첫 측정에만 보여주기 위한 값이다. */
    fun isTrackingGuideShown(): Boolean =
        settings.getBoolean(PreferencesKeys.App.TRACKING_GUIDE_SHOWN, false)

    fun markTrackingGuideShown() {
        settings.putBoolean(PreferencesKeys.App.TRACKING_GUIDE_SHOWN, true)
    }

    /** 권한 안내 화면을 이미 본 적이 있는가. 수면 가이드와 마찬가지로 최초 1회만 보여준다. */
    fun isPermissionGuideShown(): Boolean =
        settings.getBoolean(PreferencesKeys.App.PERMISSION_GUIDE_SHOWN, false)

    fun markPermissionGuideShown() {
        settings.putBoolean(PreferencesKeys.App.PERMISSION_GUIDE_SHOWN, true)
    }
}
