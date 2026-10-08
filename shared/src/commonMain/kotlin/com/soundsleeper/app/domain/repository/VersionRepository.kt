package com.soundsleeper.app.domain.repository

interface VersionRepository {
    suspend fun getLatestVersion(): String?
}