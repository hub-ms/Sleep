package com.soundsleeper.app.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundsleeper.app.domain.model.LegalDocument
import com.soundsleeper.app.domain.model.LegalSection
import com.soundsleeper.app.enum_.LegalType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.ic_caret_left
import com.soundsleeper.app.resources.ic_caret_up
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.ui.theme.bodyHighlight
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
private val bodyLineHeight = 22.sp

@Composable
fun LegalContent(
    type: LegalType,
    state: LegalUiState,
    onRetry: () -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    when (state) {
        LegalUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        LegalUiState.Error -> LegalErrorView(onRetry = onRetry, onBackClick = onBackClick)
        is LegalUiState.Success -> LegalDocumentContent(
            title = type.path,
            document = state.document,
            onBackClick = onBackClick,
        )
    }
}

@Composable
private fun LegalDocumentContent(
    title: String,
    document: LegalDocument,
    onBackClick: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 첫 항목이 조금이라도 밀려 올라갔으면 "맨 위"가 아니다. derivedStateOf 로 감싸서
    // 스크롤 픽셀이 바뀔 때마다 화면 전체가 다시 그려지지 않게 한다.
    val showScrollTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 이 화면에는 앱바가 없어서, 들어오면 시스템 백 버튼 말고는 나갈 길이
                // 보이지 않았다. 페이월에서 열면 더더욱 돌아갈 곳이 불분명하다.
                if (onBackClick != null) {
                    Row(
                        modifier = Modifier
                            .clickable { onBackClick() }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_caret_left),
                            contentDescription = "뒤로가기",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "뒤로",
                            style = MaterialTheme.typography.bodyText,
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "시행일: ${document.effectiveDate} · 최종 수정일: ${document.updatedDate}",
                    style = MaterialTheme.typography.caption,
                    color = Color.White
                )
            }

            LazyColumn(
                state = listState,
                // fillMaxSize 는 헤더가 커지면 남는 높이가 0이 될 수 있다.
                // weight 로 남은 공간을 명시적으로 가져간다.
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
            ) {
                item {
                    // 빠른 이동 목록을 헤더에 고정하지 않고 첫 항목으로 둔 이유: 조항이 11~12개라
                    // 세로로 세우면 화면을 다 차지해서, 본문과 함께 위로 밀려 사라지는 편이 낫다.
                    QuickNavList(
                        sections = document.sections,
                        onSectionClick = { index ->
                            // 0번 항목이 이 목록 자신이라 본문 인덱스는 한 칸씩 밀린다.
                            scope.launch { listState.animateScrollToItem(index + 1) }
                        }
                    )
                }
                items(document.sections, key = {it.number}) { section ->
                    LegalSectionBlock(section)
                    HorizontalDivider(color = surface)
                }
                item {
                    Text(
                        text = document.footerNote,
                        modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClickLabel = "맨 위로 이동") {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                shape = CircleShape,
                color = primary,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(Res.drawable.ic_caret_up),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/** 조항 목록을 세로로 훑어보고 눌러서 바로 이동하는 메뉴. */
@Composable
private fun QuickNavList(
    sections: List<LegalSection>,
    onSectionClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "빠른 이동",
            modifier = Modifier.padding(bottom = 4.dp),
            style = MaterialTheme.typography.caption,
            color = Color.White
        )
        sections.forEachIndexed { index, section ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSectionClick(index) },
                shape = RoundedCornerShape(10.dp),
                color = surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${section.number}.",
                        modifier = Modifier.width(24.dp),
                        style = MaterialTheme.typography.bodyText,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = section.heading,
                        style = MaterialTheme.typography.bodyText,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** 조항 하나. 예전에는 접었다 펴는 아코디언이었지만, 지금은 항상 펼쳐진 상태로 둔다. */
@Composable
private fun LegalSectionBlock(section: LegalSection) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "${section.number}.",
                style = MaterialTheme.typography.bodyHighlight,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                modifier = Modifier.weight(1f),
                text = section.heading,
                style = MaterialTheme.typography.bodyHighlight,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        LegalSectionBody(section)
    }
}

@Composable
private fun LegalSectionBody(section: LegalSection) {
    val paragraphStyle = MaterialTheme.typography.bodyText.copy(lineHeight = bodyLineHeight)

    Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        section.paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                style = paragraphStyle,
                color = Color.White
            )
        }

        if (section.bullets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                section.bullets.forEach { bullet ->
                    // 불릿을 고정폭 칸에 넣어, 문장이 두 줄 이상 넘어가도 들여쓰기가 맞는다.
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "•",
                            modifier = Modifier.width(16.dp),
                            style = paragraphStyle,
                            color = Color.White
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = bullet,
                            style = paragraphStyle,
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (section.tableRows.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = surface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    section.tableRows.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = value,
                                style = paragraphStyle,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        section.callout?.let { callout ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = primary.copy(alpha = 0.08f)
            ) {
                Text(
                    text = callout,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.caption,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun LegalErrorView(onRetry: () -> Unit, onBackClick: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "내용을 불러오지 못했어요.",
            style = MaterialTheme.typography.bodyText,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
        if (onBackClick != null) {
            TextButton(onClick = onBackClick) { Text("뒤로") }
        }
    }
}