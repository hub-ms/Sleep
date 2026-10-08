package com.soundsleeper.app.data.remote.api

import com.soundsleeper.app.domain.model.LegalDocument
import com.soundsleeper.app.enum_.LegalType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get

class LegalApi(private val client: HttpClient) {
    suspend fun getDocument(type: LegalType): LegalDocument =
        client.get("legal/${type.path}") {
            expectSuccess = true // 4xx/5xx면 예외를 던져 폴백으로 넘어가게 한다
        }.body()
}