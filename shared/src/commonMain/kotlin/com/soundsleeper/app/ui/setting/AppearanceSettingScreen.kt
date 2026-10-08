package com.soundsleeper.app.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.enum_.AppLanguage
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.appearance_language_en
import com.soundsleeper.app.resources.appearance_language_ko
import com.soundsleeper.app.resources.appearance_language_system
import com.soundsleeper.app.resources.appearance_language_title
import com.soundsleeper.app.resources.common_back
import com.soundsleeper.app.resources.ic_caret_left
import com.soundsleeper.app.ui.component.SelectableCard
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
@Composable
fun LanguageSettingContent(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onBackClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(Res.drawable.ic_caret_left),
                        contentDescription = stringResource(Res.string.common_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(Res.string.appearance_language_title),
                    style = MaterialTheme.typography.sectionTitle,
                    color = Color.White
                )
            }
            AppLanguage.entries.forEach { language ->
                val languageText = when (language) {
                    AppLanguage.SYSTEM -> stringResource(Res.string.appearance_language_system)
                    AppLanguage.KO -> stringResource(Res.string.appearance_language_ko)
                    AppLanguage.EN -> stringResource(Res.string.appearance_language_en)
                }
                SelectableCard(
                    text = languageText,
                    isSelected = language == currentLanguage,
                    onClick = { onSelectLanguage(language) },
                )
            }
        }
    }
}

