@file:OptIn(ExperimentalForeignApi::class)

package com.sleepytime.shared.platform

import com.sleepytime.shared.data.local.SleepMusicEntity
import com.sleepytime.shared.domain.model.SleepMusic
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle

actual fun createMusicPlayer(): MusicPlayer = IosMusicPlayer()

class IosMusicPlayer : MusicPlayer {
    private var player: AVAudioPlayer? = null

    override suspend fun play(musicName: String, startSeconds: Int, volume: Float) {
        val url = NSBundle.mainBundle.URLForResource(musicName, "mp3") ?: return
        player = AVAudioPlayer(contentsOfURL = url, error = null).apply {
            this.volume = volume
            numberOfLoops = -1  // 무한 반복
            prepareToPlay()
            play()
        }
    }
    override suspend fun pause()  { player?.pause() }
    override suspend fun resume() { player?.play() }
    override suspend fun seek(seconds: Int) { player?.seekToTime(seconds.toDouble()) }
    override fun stop()   { player?.stop(); player = null }
    override fun setVolume(volume: Float) { player?.volume = volume }
    override val isPlaying get() = player?.playing ?: false
    override suspend fun loadMusic(musicId: String?): SleepMusic? {
        TODO("Not yet implemented")
    }
}