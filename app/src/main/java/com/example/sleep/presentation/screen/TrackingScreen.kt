package com.example.sleep.presentation.screen

import RemainingTime
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sleep.R
import com.example.sleep.presentation.contract.TrackingContract

//data class Image(var imageRes: Int)
//data class Title(var text: String)
//data class SleepMusic(var title: String, var imageRes: Int, var musicRes: Int, var duration: Long)

@Composable
fun TrackingScreen(
    state: TrackingContract.TrackingState, onIntent: (TrackingContract.TrackingIntent) -> Unit, onNavigateMusic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, Color.Blue)
            .background(Color(0xFF0A021D))
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RemainingTime(
            hour = state.remainingHour, minute = state.remainingMinute
        )
        MusicPlayer(
            title = state.musicTitle,
            imageRes = state.musicImageRes,
            isPlaying = state.isPlaying,
        )
    }
}


@Composable
fun MusicPlayer(
    title: String,
    imageRes: Int,
    isPlaying: Boolean,
) {
    val icon = if (isPlaying) R.drawable.pause else R.drawable.play

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White)

        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    }
}