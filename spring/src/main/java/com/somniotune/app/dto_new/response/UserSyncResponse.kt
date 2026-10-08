package com.somniotune.app.dto_new.response

import com.somniotune.app.data.remote.dto.response.AlarmResponse
import com.somniotune.app.data.remote.dto.response.SleepSessionResponse
import com.somniotune.app.data.remote.dto.response.UserResponse

data class UserSyncResponse(
    val user: UserResponse,
    val alarms: List<AlarmResponse>,
    val sleepSessions: List<SleepSessionResponse>
)
