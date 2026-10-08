package com.soundsleeper.app.platform

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * 알람 볼륨 읽기·쓰기와 "사용자가 기기 볼륨 키로 바꿨을 때" 알림을 한곳에서 담당한다.
 *
 * 예전에는 볼륨 변화 감지만 AndroidVolumeObserver 로 떼어 두고 이 클래스가 그대로 위임했는데,
 * 두 클래스가 각각 AudioManager 를 얻어 같은 STREAM_ALARM 비율 계산을 중복하고 있었다.
 * 감시 대상이 결국 같은 스트림이라 나눌 이유가 없어 하나로 합쳤다.
 */
class AndroidAudioSystem(
    private val context: Context,
) : AudioSystem {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var listener: ((Float) -> Unit)? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            listener?.invoke(readAlarmVolumeRatio())
        }
    }

    override fun getSystemAlarmVolume(): Float = readAlarmVolumeRatio()

    override fun setSystemAlarmVolume(volume: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val target = (volume * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
    }

    override fun observeVolumeChanges(onChanged: (Float) -> Unit) {
        // 같은 ContentObserver 를 두 번 등록하면 콜백이 중복으로 들어온다. 먼저 정리하고 등록한다.
        unregisterVolumeObserver()
        listener = onChanged
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer
        )
    }

    override fun unregisterVolumeObserver() {
        context.contentResolver.unregisterContentObserver(observer)
        listener = null
    }

    /**
     * 0f~1f 비율. 🐛 예전 getSystemAlarmVolume() 은 max 가 0인 기기에서 0으로 나눠 NaN 을 돌려줬다
     * (옵저버 쪽에만 가드가 있었다). 두 경로가 이제 같은 계산을 쓰므로 가드도 한 번만 둔다.
     */
    private fun readAlarmVolumeRatio(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (max == 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() / max
    }
}
