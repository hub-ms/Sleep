package com.soundsleeper.app.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.enum_.AppLanguage
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.appearance_language_en
import com.soundsleeper.app.resources.appearance_language_ko
import com.soundsleeper.app.resources.appearance_language_system
import com.soundsleeper.app.resources.appearance_language_title
import com.soundsleeper.app.ui.component.ScreenTopBar
import com.soundsleeper.app.ui.component.SelectableCard
import com.soundsleeper.app.ui.theme.SleepTheme.background
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
            // 예전에는 statusBarsPadding 이 없어 제목이 상태바 아래로 파고들었다.
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenTopBar(
            title = stringResource(Res.string.appearance_language_title),
            onBackClick = onBackClick,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
