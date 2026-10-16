package com.soundsleeper.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.MainActivity
import com.zoyi.channel.plugin.android.ChannelIO
import com.zoyi.channel.plugin.android.open.config.BootConfig
import com.zoyi.channel.plugin.android.open.enumerate.BootStatus
import com.zoyi.channel.plugin.android.open.model.Profile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalTime::class,
    ExperimentalMaterial3Api::class,
    InternalVoyagerApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalSettingsApi::class,
)
class AndroidChatSupportManager(
    private val context: Context,
    private val channelTalkPluginKey: String,
) : ChatSupportManager {

    override suspend fun boot(userId: String?, email: String?, nickname: String?, memberHash: String?): Boolean {
        val profile = Profile.create().apply {
            nickname?.let { setName(it) }
            email?.let { setEmail(it) }
        }
        val bootConfig = BootConfig.create(channelTalkPluginKey)
            .setMemberId(userId)
            .setMemberHash(memberHash)
            .setProfile(profile)

        return suspendCancellableCoroutine { cont ->
            ChannelIO.boot(bootConfig) { status, _ ->
                Napier.d("ChannelIO boot status=$status")
                if (cont.isActive) cont.resume(status == BootStatus.SUCCESS)
            }
        }
    }

    @UnstableApi
    override fun showMessenger() {
        val activity = context.findActivity()
        Napier.d("ChannelIO showMessenger activity=$activity")
        if (activity == null) return
        ChannelIO.showMessenger(activity)
    }

    override fun shutdown() {
        ChannelIO.shutdown()
    }

    @UnstableApi
    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return MainActivity.instance?.get()
    }
}