package com.sleepytime.app.controller_new

import com.sleepytime.app.dto_new.SleepSessionDto
import com.sleepytime.app.service_new.SleepService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sleep")
class SleepController(
    private val sleepService: SleepService
) {

    @GetMapping
    fun getMySleep(): ResponseEntity<List<SleepSessionDto>> {
        val userId = getUserId()
        return ResponseEntity.ok(sleepService.getUserSleepData(userId))
    }

    @DeleteMapping("/me")
    fun deleteMySleep(): ResponseEntity<Unit> {
        val userId = getUserId()
        sleepService.deleteUserSleepData(userId)
        return ResponseEntity.ok().build()
    }

    private fun getUserId(): Long {
        val auth = SecurityContextHolder.getContext().authentication
        return auth?.name?.toLong() ?: throw IllegalArgumentException("User ID not found in authentication")
    }
}