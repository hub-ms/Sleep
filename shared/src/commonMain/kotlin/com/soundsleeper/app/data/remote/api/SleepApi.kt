package com.soundsleeper.app.data.remote.api

import com.soundsleeper.app.data.remote.dto.request.SleepSessionRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class SleepApi(
    private val client: HttpClient,
) {
    suspend fun saveSession(
        request: SleepSessionRequest
    ): Result<Unit> = runCatching {
        val response = client.post("/sleep/session") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "세션 저장 실패 (${response.status.value}) : ${
                    response.bodyAsText()
                }"
            )
        }
    }
}