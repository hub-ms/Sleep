package com.sleepytime.shared.platform

import com.sleepytime.shared.data.local.SleepMusicEntity
import com.sleepytime.shared.domain.model.SleepMusic

actual fun createMusicPlayer(): MusicPlayer = NoOpMusicPlayer()

class NoOpMusicPlayer : MusicPlayer {
    override suspend fun play(musicName: String, startSeconds: Int, volume: Float) {
        TODO("Not yet implemented")
    }
    override suspend fun loadMusic(musicId: String?): SleepMusic? {
        TODO("Not yet implemented")
    }
    override suspend fun pause()  {}
    override suspend fun resume() {}
    override suspend fun seek(seconds: Int) {
        TODO("Not yet implemented")
    }
    override fun stop()   {}
    override fun setVolume(volume: Float) {}
    override val isPlaying: Boolean get() = false
}