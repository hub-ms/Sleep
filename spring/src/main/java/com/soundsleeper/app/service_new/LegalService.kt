package com.soundsleeper.app.service_new   // 현재 사용 중인 패키지 그대로

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.soundsleeper.app.dto_new.response.LegalDocumentResponse
import com.soundsleeper.app.enum_.LegalType
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class LegalService {
    private val objectMapper = jacksonObjectMapper()

    private val documents: Map<LegalType, LegalDocumentResponse> =
        LegalType.entries.associateWith { load(it) }

    fun getDocument(type: LegalType): LegalDocumentResponse = documents.getValue(type)

    private fun load(type: LegalType): LegalDocumentResponse =
        ClassPathResource("legal/${type.path}.json").inputStream.use {
            objectMapper.readValue(it, LegalDocumentResponse::class.java)
        }
}