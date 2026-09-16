package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.enum_.FaqCategory
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_caret_left
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_help
import com.sleepytime.shared.ui.component.FaqItem
import com.sleepytime.shared.ui.component.SelectableChip
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.sectionTitle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * 고객 지원 및 도움말 화면 (알라미/나이틀리 스타일 개선)
 */
@Composable
fun SupportContent(
    allItems: List<FaqItem>,
    onChatClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.gradients.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "도움말 및 지원",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
        )
        FaqSection(allItems, modifier = Modifier.weight(1f))
        SettingCard {
            ContactItem("고객센터 1:1 상담", "상담원과 채팅으로 문의하세요", onClick = onChatClick)
        }
    }
}

@Composable
fun FaqSection(allItems: List<FaqItem>, modifier: Modifier = Modifier) {
    var selectedCategory by remember { mutableStateOf(FaqCategory.SLEEP) }

    val filteredItems = remember(selectedCategory) {
        allItems.filter { item ->
            val matchesCategory = item.category == selectedCategory
            matchesCategory
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                items(FaqCategory.entries) { category ->
                    SelectableChip(
                        text = category.displayName,
                        isSelected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }
            if (listState.canScrollBackward) {
                IconButton(
                    modifier = Modifier.align(Alignment.CenterStart).background(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        CircleShape
                    ),
                    onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_caret_left),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            if (listState.canScrollForward) {
                IconButton(
                    modifier = Modifier.align(Alignment.CenterEnd).background(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        CircleShape
                    ),
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_caret_right),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.05f),
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(filteredItems) { item ->
                    Column(
                        modifier = Modifier
                            .border(2.dp, Color.Green),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = item.answer,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableFaqItem(item: FaqItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = item.answer,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4
            )
        }
    }
}
@Composable
fun ContactItem(title: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(description, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
            Icon(
                painter = painterResource(Res.drawable.ic_caret_right),
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = "운영시간: 평일 10:00~18:00",
            style = MaterialTheme.typography.labelSmall,
            color = Color.LightGray
        )
    }
}
