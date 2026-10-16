package com.soundsleeper.app.ui.auth

import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.signup_loading_title
import com.soundsleeper.app.resources.signup_loading_description
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.caption

@Composable
fun SignUpLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = primary)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.signup_loading_title),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.signup_loading_description),
                modifier = Modifier.padding(horizontal = 32.dp),
                style = MaterialTheme.typography.caption,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
