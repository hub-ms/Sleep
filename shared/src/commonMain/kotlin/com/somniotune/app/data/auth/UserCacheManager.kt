package com.somniotune.app.data.auth

import com.russhwolf.settings.ObservableSettings
import com.somniotune.app.data.local.dao.UserDao
import com.somniotune.app.data.local.mapper.toUserEntity
import com.somniotune.app.domain.model.SubscriptionStatus
import com.somniotune.app.domain.model.User

class UserCacheManager(
    private val userDao: UserDao,
    private val settings: ObservableSettings,
) {
    fun saveUser(user: User) {
        userDao.upsertUser(user.toUserEntity())

        settings.putLong("logged_in_user_id", user.userId)
        settings.putString("key_user_email", user.email.orEmpty())
        user.profileImageUrl?.let {
            settings.putString("key_user_profile_img", it)
        }
    }
    fun updatePremiumStatus(subscriptionStatus: SubscriptionStatus) {
        userDao.updatePremiumStatus(
            subscriptionStatus.isPremium
        )
    }
    fun clearUser() {
        userDao.deleteUser()

        settings.remove("logged_in_user_id")
        settings.remove("key_user_email")
        settings.remove("key_user_profile_img")
        settings.remove("key_user_nickname")
    }
}