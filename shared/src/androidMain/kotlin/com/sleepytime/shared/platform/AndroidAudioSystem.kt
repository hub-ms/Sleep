package com.sleepytime.shared.platform

import android.content.Context
import android.media.AudioManager

class AndroidAudioSystem(
    private val context: Context,
    private val androidVolumeObserver: AndroidVolumeObserver
) : AudioSystem {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun getSystemAlarmVolume(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        return current.toFloat() / max
    }
    override fun setSystemAlarmVolume(volume: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val target = (volume * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume (AudioManager.STREAM_ALARM, target, 0)
    }
    override fun observeVolumeChanges(onChanged: (Float) -> Unit) {
        androidVolumeObserver.register(onChanged)
    }
    override fun unregisterVolumeObserver() = androidVolumeObserver.unregister()
}