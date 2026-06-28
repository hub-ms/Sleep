package com.sleepytime.shared.data.local.dao

import com.sleepytime.shared.data.local.UserEntity
import kotlinx.coroutines.flow.Flow
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.sleepytime.shared.data.local.generated.SleepDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

class UserDao(private val db: SleepDatabase) {
    private val queries = db.userEntityQueries

    suspend fun upsertUser(user: UserEntity) {
        queries.insertUser(user)
    }

    suspend fun getUser(): UserEntity? {
        return queries.getUser().executeAsOneOrNull()
    }

    fun observeUser(): Flow<UserEntity?> {
        return queries.getUser().asFlow().mapToOneOrNull(Dispatchers.IO)
    }

    suspend fun clearUser() {
        queries.deleteUser()
    }
}
