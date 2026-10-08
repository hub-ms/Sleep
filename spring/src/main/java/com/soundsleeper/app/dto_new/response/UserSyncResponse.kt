package com.soundsleeper.app.dto_new.response

import com.soundsleeper.app.data.remote.dto.response.AlarmResponse
import com.soundsleeper.app.data.remote.dto.response.SleepSessionResponse
import com.soundsleeper.app.data.remote.dto.response.UserResponse

data class UserSyncResponse(
    val user: UserResponse,
    val alarms: List<AlarmResponse>,
    val sleepSessions: List<SleepSessionResponse>
)
