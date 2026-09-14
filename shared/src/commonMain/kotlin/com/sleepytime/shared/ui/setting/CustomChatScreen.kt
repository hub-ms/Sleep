package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_caret_left
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.ui.theme.SleepTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Renight 스타일의 1:1 채팅 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomChatContent(onBackClick: () -> Unit) {
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf(
        ChatMessage("안녕하세요! SleepyTime 고객센터입니다. 무엇을 도와드릴까요?", false),
        ChatMessage("수면 측정이 안돼요 ㅠㅠ", true),
        ChatMessage("불편을 드려 죄송합니다. 현재 앱 버전과 기기명을 알려주시면 빠르게 확인해드릴게요!", false)
    ) }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("1:1 문의", style = MaterialTheme.typography.titleMedium, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(Res.drawable.ic_caret_left), null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E2235) // 배경색 직접 지정
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                color = Color.White.copy(alpha = 0.02f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("메시지를 입력하세요", color = Color.Gray, fontSize = 14.sp) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        maxLines = 3
                    )
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                messages.add(ChatMessage(messageText, true))
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_caret_right),
                            contentDescription = "전송",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(SleepTheme.gradients.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 20.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isMine = message.isMine
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine) {
            Text(
                "CS 매니저",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) 
                    else Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 2.dp,
                bottomEnd = if (isMine) 2.dp else 16.dp
            ),
            border = if (!isMine) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isMine) Color.Black else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

data class ChatMessage(val text: String, val isMine: Boolean)
