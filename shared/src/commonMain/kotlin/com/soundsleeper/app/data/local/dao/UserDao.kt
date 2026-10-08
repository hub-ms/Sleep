package com.soundsleeper.app.data.local.dao

import com.soundsleeper.app.data.local.UserEntity
import kotlinx.coroutines.flow.Flow
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.soundsleeper.app.data.local.generated.SleepDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

class UserDao(db: SleepDatabase) {
    private val queries = db.userEntityQueries

    fun upsertUser(user: UserEntity) = queries.upsertUser(user)
    fun getUser(): UserEntity? = queries.getUser().executeAsOneOrNull()
    fun observeUser(): Flow<UserEntity?> = queries.getUser().asFlow().mapToOneOrNull(Dispatchers.IO)
    fun deleteUser() = queries.deleteUser()
    fun updatePremiumStatus(isPremium: Boolean) = queries.updatePremiumStatus(isPremium)
}
