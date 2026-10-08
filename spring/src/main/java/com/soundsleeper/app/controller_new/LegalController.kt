package com.soundsleeper.app.legal

import com.soundsleeper.app.dto_new.response.LegalDocumentResponse
import com.soundsleeper.app.service_new.LegalService
import com.soundsleeper.app.enum_.LegalType
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/legal")
class LegalController(
    private val legalService: LegalService,
) {
    // GET /legal/terms, GET /legal/privacy (비인증 허용: SecurityConfig 참고)
    @GetMapping("/{type}")
    fun getDocument(@PathVariable type: String): ResponseEntity<LegalDocumentResponse> {
        val legalType = LegalType.fromPath(type)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "지원하지 않는 문서입니다: $type")

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
            .body(legalService.getDocument(legalType))
    }
}