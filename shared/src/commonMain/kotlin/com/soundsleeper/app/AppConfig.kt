package com.soundsleeper.app

data class AppConfig(
    val baseUrl: String,
    val googleOAuthClientId: String,
    val channelTalkPluginKey: String,
    val revenueCatApiKey: String,
    val isDebug: Boolean,
    val versionName: String,
)