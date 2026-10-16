package com.soundsleeper.app.data.auth

import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.data.local.dao.UserDao
import com.soundsleeper.app.data.local.mapper.toUserEntity
import com.soundsleeper.app.domain.model.SubscriptionStatus
import com.soundsleeper.app.domain.model.User

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