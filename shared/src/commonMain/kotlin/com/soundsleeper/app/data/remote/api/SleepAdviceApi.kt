package com.soundsleeper.app.data.remote.api

import com.soundsleeper.app.data.remote.dto.request.SleepAdviceRequest
import com.soundsleeper.app.data.remote.dto.response.SleepAdviceResponse
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SleepAdviceApi(
    private val client: HttpClient
) {
    suspend fun generateAdvice(request: SleepAdviceRequest): Result<SleepAdviceResponse> = runCatching {
        val response = client.post("api/sleep-advice") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("generateAdvice API 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Generate advice failed: ${response.status} - $errorBody")
        }
        response.body<SleepAdviceResponse>()
    }.onFailure { Napier.e("generateAdvice 예외: ${it.message}", it) }
}
