package com.soundsleeper.app.ui.auth

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

import com.soundsleeper.app.platform.EmailLauncher
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.common_confirm_short
import com.soundsleeper.app.resources.email_address_label
import com.soundsleeper.app.resources.email_code_hint
import com.soundsleeper.app.resources.email_code_label
import com.soundsleeper.app.resources.email_condition_at_symbol
import com.soundsleeper.app.resources.email_condition_domain
import com.soundsleeper.app.resources.email_connect_cta
import com.soundsleeper.app.resources.email_direct_input
import com.soundsleeper.app.resources.email_headline
import com.soundsleeper.app.resources.email_resend_code
import com.soundsleeper.app.resources.email_send_code
import com.soundsleeper.app.resources.ic_check
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.secondary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun EmailAuthContent(
    authState: AuthContract.State,
    onEmailChanged: (String) -> Unit,
    onSendAuthCode: (String) -> Unit,
    onSubmitAuthCode: (String) -> Unit,
    emailLauncher: EmailLauncher,
    isConnectFlow: Boolean = false,
) {
    val emailFocusRequester = remember { FocusRequester() }
    // 메일로 받은 6자리 코드. 예전에는 입력칸 자체가 없어서 인증을 끝낼 방법이 없었다.
    var authCode by remember { mutableStateOf("") }
    var showDomain by remember { mutableStateOf(false) }
    // DomainListInline 의 onDomainSelected 콜백은 @Composable 이 아니라 그 안에서
    // stringResource 를 부를 수 없다. 바깥 Composable 스코프에서 미리 읽어 둔다.
    val directInputLabel = stringResource(Res.string.email_direct_input)
    val domains = listOf(directInputLabel, "naver.com", "gmail.com", "kakao.com", "hanmail.net", "daum.net")

    LaunchedEffect(Unit) {
        emailFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.email_headline),
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
                ConditionItem(text = stringResource(Res.string.email_condition_at_symbol), isMet = authState.hasAtSymbol)
                ConditionItem(text = stringResource(Res.string.email_condition_domain), isMet = authState.hasValidDomain)
            }

            if (showDomain) {
                DomainListInline(
                    domains = domains,
                    onDomainSelected = { domain ->
                        val localPart = authState.email.substringBefore('@')
                        val fullEmail = if (domain == directInputLabel) "$localPart@" else "$localPart@$domain"
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
                        containerColor = if (authState.isEmailValid) primary else Color.White
                    )
                ) {
                    Text(
                        text = if (authState.isCodeSent) stringResource(Res.string.email_resend_code) else stringResource(Res.string.email_send_code),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // 코드를 보낸 뒤에만 입력칸을 연다. 보내기 전에 보여주면 무엇을 넣어야 할지 모른다.
        if (authState.isCodeSent) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.email_code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = authCode,
                onValueChange = { input -> authCode = input.filter { it.isDigit() }.take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.email_code_label)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = primary,
                    unfocusedLabelColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSubmitAuthCode(authCode) },
                enabled = authCode.length == 6 && !authState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Text(text = if (isConnectFlow) stringResource(Res.string.email_connect_cta) else stringResource(Res.string.common_confirm_short), color = MaterialTheme.colorScheme.onPrimary)
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
        email.isEmpty() -> Color.White
        isValid -> primary
        else -> MaterialTheme.colorScheme.error
    }

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChanged,
        label = { Text(stringResource(Res.string.email_address_label)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        textStyle = TextStyle(Color.White, fontSize = 16.sp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.White,
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor,
            cursorColor = primary
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
            tint = if (isMet) secondary else Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (isMet) secondary else Color.White
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
        colors = CardDefaults.cardColors(containerColor = surface),
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
                    Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}
