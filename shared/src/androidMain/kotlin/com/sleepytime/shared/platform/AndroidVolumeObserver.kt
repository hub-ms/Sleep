package com.sleepytime.shared.platform

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class AndroidVolumeObserver(
    private val context: Context,
) : VolumeObserver {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var listener: ((Float) -> Unit)? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val volume = if (max == 0) 0f else current.toFloat() / max
            listener?.invoke(volume)
        }
    }
    override fun register(onChanged: (Float) -> Unit) {
        this.listener = onChanged
        context.contentResolver.registerContentObserver (
            Settings.System .CONTENT_URI,
            true,
            observer
        )
    }
    override fun unregister() {
        context.contentResolver . unregisterContentObserver (observer)
        listener = null
    }
}