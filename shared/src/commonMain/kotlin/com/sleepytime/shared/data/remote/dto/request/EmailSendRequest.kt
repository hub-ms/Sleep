package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class EmailSendRequest(val email: String)