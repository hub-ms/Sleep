package com.soundsleeper.app.ui.setting

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.soundsleeper.app.domain.repository.AuthRepository
import com.soundsleeper.app.platform.ChatSupportManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch

class ChatViewModel(
    private val authRepository: AuthRepository,
    private val chatSupportManager: ChatSupportManager,
)  : AppScopedScreenModel() {

    fun openChat() {
        modelScope.launch {
            val user = runCatching { authRepository.getUser() }.getOrNull()
            if (user != null) {
                val memberHash = authRepository.getChannelTalkHash()
                    .onFailure { Napier.e("getChannelTalkHash 실패: ${it.message}", it) }
                    .getOrNull()
                chatSupportManager.boot(user.userId.toString(), user.email, user.nickname, memberHash)
            }
            chatSupportManager.showMessenger()
        }
    }
}
