package com.sleepytime.shared.ui.setting

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.platform.ChatSupportManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch

class ChatViewModel(
    private val authRepository: AuthRepository,
    private val chatSupportManager: ChatSupportManager,
) : ScreenModel {

    fun openChat() {
        screenModelScope.launch {
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
