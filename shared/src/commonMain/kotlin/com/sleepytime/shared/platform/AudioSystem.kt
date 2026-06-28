package com.sleepytime.shared.platform

interface AudioSystem {
    fun getSystemAlarmVolume(): Float
    fun setSystemAlarmVolume(volume: Float)
    fun observeVolumeChanges(onChanged: (Float) -> Unit)
    fun unregisterVolumeObserver()
}