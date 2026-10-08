package com.somniotune.app.platform

import com.somniotune.app.domain.model.SleepMusic

actual fun createMusicPlayer(): MusicPlayer = NoOpMusicPlayer()

class NoOpMusicPlayer : MusicPlayer {
    override suspend fun play(musicName: String, type: SoundType,  startSeconds: Int, volume: Float) {}
    override suspend fun loadMusic(musicId: String?): SleepMusic? = null
    override suspend fun pause()  {}
    override suspend fun resume() {}
    override suspend fun seek(seconds: Int) {}
    override fun stop()   {}
    override fun setVolume(volume: Float) {}
    override val isPlaying: Boolean get() = false
}