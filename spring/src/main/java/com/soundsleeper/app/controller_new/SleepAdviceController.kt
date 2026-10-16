package com.soundsleeper.app.controller_new

import com.soundsleeper.app.dto_new.request.SleepAdviceRequest
import com.soundsleeper.app.dto_new.response.SleepAdviceResponse
import com.soundsleeper.app.service_new.SleepAdviceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sleep-advice")
class SleepAdviceController(
    private val sleepAdviceService: SleepAdviceService
) {
    @PostMapping
    fun generate(
        @RequestBody request: SleepAdviceRequest
    ): ResponseEntity<SleepAdviceResponse> =
        ResponseEntity.ok(sleepAdviceService.generate(request))
}
