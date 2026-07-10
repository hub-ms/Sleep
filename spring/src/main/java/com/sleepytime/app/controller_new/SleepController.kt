package com.sleepytime.app.controller_new

import com.sleepytime.app.dto_new.SleepSessionDto
import com.sleepytime.app.dto_new.sleep.SleepSessionCreateRequest
import com.sleepytime.app.dto_new.sleep.SleepSessionUpdateRequest
import com.sleepytime.app.dto_new.sleep.SleepStartRequest
import com.sleepytime.app.service_new.SleepService
import com.sleepytime.shared.data.remote.dto.response.MonthlySleepStatsResponse
import com.sleepytime.shared.data.remote.dto.response.SleepSessionResponse
import com.sleepytime.shared.data.remote.dto.response.WeeklySleepStatsResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/sleep-session")
class SleepController(
    private val sleepService: SleepService
) {
    @PostMapping
    fun createSession(
        @RequestBody request: SleepSessionCreateRequest
    ): ResponseEntity<SleepSessionResponse> {
        val response = sleepService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    @PostMapping("/start")
    fun startSleep(
        @RequestBody request: SleepStartRequest
    ): ResponseEntity<SleepSessionResponse> {
        val response = sleepService.start(request.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    @PatchMapping("/{sessionId}/end")
    fun endSleep(
        @PathVariable sessionId: Long
    ): ResponseEntity<SleepSessionResponse> {
        val response = sleepService.end(sessionId)
        return ResponseEntity.ok(response)
    }
    @GetMapping("/{sessionId}")
    fun getById(
        @PathVariable sessionId: Long
    ): ResponseEntity<SleepSessionResponse> {
        val response = sleepService.getById(sessionId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/user/{userId}")
    fun getByUserId(
        @PathVariable userId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<SleepSessionResponse>> {
        val response = sleepService.getByUserId(userId, pageable)
        return ResponseEntity.ok(response)
    }
    @GetMapping("/user/{userId}/daily")
    fun getDaily(
        @PathVariable userId: Long,
        @RequestParam date: LocalDate
    ): ResponseEntity<List<SleepSessionResponse>> {
        val response = sleepService.getDaily(userId, date)
        return ResponseEntity.ok(response)
    }
    @GetMapping("/user/{userId}/weekly")
    fun getWeekly(
        @PathVariable userId: Long,
        @RequestParam date: LocalDate
    ): ResponseEntity<WeeklySleepStatsResponse> {
        val response = sleepService.getWeekly(userId, date)
        return ResponseEntity.ok(response)
    }
    @GetMapping("/user/{userId}/monthly")
    fun getMonthly(
        @PathVariable userId: Long,
        @RequestParam year: Int,
        @RequestParam month: Int
    ): ResponseEntity<MonthlySleepStatsResponse> {
        val response = sleepService.getMonthly(userId, year, month)
        return ResponseEntity.ok(response)
    }
    @PatchMapping("/{sessionId}")
    fun updateSession(
        @PathVariable sessionId: Long,
        @RequestBody request: SleepSessionUpdateRequest
    ): ResponseEntity<SleepSessionResponse> {
        val response = sleepService.update(sessionId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{sessionId}")
    fun deleteSession(
        @PathVariable sessionId: Long
    ): ResponseEntity<Unit> {
        sleepService.delete(sessionId)
        return ResponseEntity.noContent().build()
    }
}