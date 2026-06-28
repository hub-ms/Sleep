package com.sleepytime.shared.platform

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.sleepytime.shared.data.local.generated.SleepDatabase
import com.sleepytime.shared.data.local.mapper.toDomain
import com.sleepytime.shared.data.local.mapper.toSleepMusicDomain
import com.sleepytime.shared.domain.model.SleepMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.getValue

actual fun createMusicPlayer(): MusicPlayer = AndroidMusicPlayer()

class AndroidMusicPlayer: MusicPlayer, KoinComponent {
    private val context: Context by inject()
    private val database: SleepDatabase by inject()
    private var player: ExoPlayer? = null

    private var currentMusicName: String? = null
    private var pausedPosition = 0

    override val isPlaying get() = player?.isPlaying ?: false

    override suspend fun play(musicName: String, startSeconds: Int, volume: Float) {
        currentMusicName = musicName
        pausedPosition = startSeconds

        release()
        Log.d("AndroidMusicPlayer", "음악 재생: $musicName")
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri("asset:///composeResources/com.sleepytime.shared.resources/files/$musicName.ogg"))
            repeatMode = Player.REPEAT_MODE_ONE
            this.volume = volume.coerceIn(0f, 1f)
            prepare()
            playWhenReady = true
        }
    }
    override suspend fun pause()  {
        player?.currentPosition?.toInt()?.let { pausedPosition = it / 1000 }
        player?.pause()
    }
    override suspend fun resume() {
        player?.seekTo(pausedPosition.toLong() * 1000)
        player?.play()
    }
    override suspend fun seek(seconds: Int) {
        player?.seekTo(seconds.toLong() * 1000)
    }
    override fun stop() { release() }
    override fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    override suspend fun loadMusic(musicName: String?): SleepMusic? {
        val musicItem = withContext(Dispatchers.IO) {
            musicName?.let {
                database.sleepMusicEntityQueries.getMusicByName(musicName).executeAsOneOrNull()
            }

        } ?: return null

        withContext(Dispatchers.Main) {
            release()
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri("asset:///files/${musicItem.musicName}.mp3"))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = musicItem.volume.coerceIn(0f, 1f)
                prepare()
                playWhenReady = true
            }
        }
        return musicItem.toSleepMusicDomain()
    }
    private fun release() {
        player?.stop()
        player?.release()
        player = null
    }
}