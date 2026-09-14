package com.sleepytime.shared.platform

interface VolumeObserver {
    fun register(onChanged: (Float) -> Unit)
    fun unregister()
}