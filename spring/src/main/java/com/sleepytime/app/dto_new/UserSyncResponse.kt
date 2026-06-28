package com.sleepytime.app.dto_new

import com.sleepytime.shared.data.remote.dto.response.AlarmResponse
import com.sleepytime.shared.data.remote.dto.response.SleepSessionResponse
import com.sleepytime.shared.data.remote.dto.response.UserResponse

data class UserSyncResponse(
    val user: UserResponse,
    val alarms: List<AlarmResponse>,
    val sleepSessions: List<SleepSessionResponse>
)