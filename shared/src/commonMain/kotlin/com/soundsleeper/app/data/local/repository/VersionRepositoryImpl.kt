package com.soundsleeper.app.data.local.repository

import com.soundsleeper.app.domain.repository.VersionRepository

class VersionRepositoryImpl : VersionRepository {
    override suspend fun getLatestVersion(): String? {
        return try {
            "1.2.0"
        } catch (e: Exception) {
            null
        }
    }
}