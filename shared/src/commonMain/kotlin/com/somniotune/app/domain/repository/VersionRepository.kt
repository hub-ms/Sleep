package com.somniotune.app.domain.repository

interface VersionRepository {
    suspend fun getLatestVersion(): String?
}