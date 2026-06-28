package com.sleepytime.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepytime.shared.ui.theme.SleepAppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

import com.sleepytime.shared.platform.EmailLauncher
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_check
import org.jetbrains.compose.resources.painterResource
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun EmailAuthContent(
    authState: AuthContract.State,
    onEmailChanged: (String) -> Unit,
    onSendAuthCode: (String) -> Unit,
    emailLauncher: EmailLauncher
) {
    val emailFocusRequester = remember { FocusRequester() }
    var showDomain by remember { mutableStateOf(false) }
    val domains = listOf("직접 입력", "naver.com", "gmail.com", "kakao.com", "hanmail.net", "daum.net")

    LaunchedEffect(Unit) {
        emailFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "반가워요!\n어떤 이메일로 시작할까요?",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        EmailInputField(
            email = authState.email,
            isValid = authState.isEmailValid,
            message = authState.message,
            focusRequester = emailFocusRequester,
            onEmailChanged = { input ->
                onEmailChanged(input)
                showDomain = input.endsWith("@")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ConditionItem(text = "@ 포함", isMet = authState.hasAtSymbol)
                ConditionItem(text = "도메인 포함", isMet = authState.hasValidDomain)
            }

            if (showDomain) {
                DomainListInline(
                    domains = domains,
                    onDomainSelected = { domain ->
                        val localPart = authState.email.substringBefore('@')
                        val fullEmail = if (domain == "직접 입력") "$localPart@" else "$localPart@$domain"
                        onEmailChanged(fullEmail)
                        showDomain = false
                    }
                )
            } else {
                Button(
                    onClick = {
                        emailLauncher.openEmailApp(authState.email)
                        onSendAuthCode(authState.email)
                    },
                    enabled = authState.isEmailValid,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (authState.isEmailValid) Color(0xFF818CF8) else Color.Gray
                    )
                ) {
                    Text(
                        text = if (authState.isCodeSent) "재발송" else "인증",
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun EmailInputField(
    email: String,
    isValid: Boolean,
    message: String?,
    focusRequester: FocusRequester,
    onEmailChanged: (String) -> Unit
) {
    val borderColor = when {
        email.isEmpty() -> Color.Gray
        isValid -> Color(0xFF4ADE80)
        else -> Color(0xFFFF6B6B)
    }

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChanged,
        label = { Text("이메일 주소") },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color.Gray,
            unfocusedLabelColor = Color.Gray,
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor,
            cursorColor = Color.White
        ),
        isError = email.isNotEmpty() && !isValid && message != null,
        singleLine = true
    )
}

@Composable
fun ConditionItem(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isMet) Color(0xFF4ADE80) else Color.Gray
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (isMet) Color(0xFF4ADE80) else Color.Gray
        )
    }
}

@Composable
fun DomainListInline(
    domains: List<String>,
    onDomainSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.width(180.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(domains) { domain ->
                Text(
                    text = domain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDomainSelected(domain) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Preview
@Composable
fun EmailAuthScreenPreview() {
    SleepAppTheme {
        EmailAuthContent(
            authState = AuthContract.State(
                email = "user@",
                hasAtSymbol = true,
                isEmailValid = false
            ),
            onEmailChanged = {},
            onSendAuthCode = {},
            emailLauncher = object : EmailLauncher {
                override fun openEmailApp(email: String) {}
            }
        )
    }
}
