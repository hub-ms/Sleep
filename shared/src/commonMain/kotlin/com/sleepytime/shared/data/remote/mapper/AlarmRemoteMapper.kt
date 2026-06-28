package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.local.AlarmEntity
import com.sleepytime.shared.data.remote.dto.request.AlarmRequest
import com.sleepytime.shared.data.remote.dto.response.AlarmResponse
import com.sleepytime.shared.domain.model.Alarm

fun AlarmResponse.toDomain() = Alarm(
    hour = hour,
    minute = minute,

    isEnabled = isEnabled,
    isVibrationEnabled = false,
    isSmartAlarmEnabled = false,
    smartAlarmRange = 20,
    isGradualVolume = false,

    sound = Alarm.Sound.DEFAULT
)
fun Alarm.toRequest(): AlarmRequest = AlarmRequest(
    hour = this.hour,
    minute = this.minute,
    isEnabled = this.isEnabled
)