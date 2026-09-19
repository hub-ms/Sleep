package com.sleepytime.shared.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.BuildConfig
import com.sleepytime.shared.MainActivity
import com.zoyi.channel.plugin.android.ChannelIO
import com.zoyi.channel.plugin.android.open.config.BootConfig
import com.zoyi.channel.plugin.android.open.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalTime::class,
    ExperimentalMaterial3Api::class,
    InternalVoyagerApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalSettingsApi::class,
)
class AndroidChatSupportManager(private val context: Context) : ChatSupportManager {

    override fun boot(userId: String?, email: String?, nickname: String?) {
        val profile = Profile.create().apply {
            nickname?.let { setName(it) }
            email?.let { setEmail(it) }
        }
        val bootConfig = BootConfig.create(BuildConfig.CHANNELTALK_PLUGIN_KEY)
            .setMemberId(userId)
            .setProfile(profile)
        ChannelIO.boot(bootConfig)
    }

    override fun showMessenger() {
        val activity = context.findActivity() ?: return
        ChannelIO.showMessenger(activity)
    }

    override fun shutdown() {
        ChannelIO.shutdown()
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return MainActivity.instance?.get()
    }
}