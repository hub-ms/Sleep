package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.SleepMusic

interface MusicPlayer {
    val isPlaying: Boolean
    suspend fun play(musicName: String, startSeconds: Int, volume: Float = 0.4f)
    suspend fun pause()
    suspend fun resume()
    suspend fun seek(seconds: Int)
    fun stop()
    fun setVolume(volume: Float)
    suspend fun loadMusic(musicName: String?): SleepMusic?
}

expect fun createMusicPlayer(): MusicPlayer