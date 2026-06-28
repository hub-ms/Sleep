package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.ui.component.FaqData
import com.sleepytime.shared.ui.component.FaqItem
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.bodyHighlight
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SupportContent(
    mainTab: Int,
    searchQuery: String,
    allItems: List<FaqItem>,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MainTabRow(
                menus = listOf("자주 묻는 질문", "고객센터"),
                selectedIndex = mainTab,
                onSelected = onTabSelected
            )

            if (mainTab == 0) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("궁금한 내용을 검색하세요") },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                modifier = Modifier.clickable { onSearchQueryChanged("") },
                                color = Color.Gray
                            )
                        }
                    }
                )

                val filteredItems = allItems.filter {
                    it.question.contains(searchQuery, true) || it.answer.contains(searchQuery, true)
                }

                when {
                    searchQuery.isBlank() -> {
                        FaqListCard {
                            allItems.forEach { item ->
                                Column {
                                    Text("Q. ${item.question}", color = Color.White)
                                }
                            }
                        }
                    }

                    filteredItems.isEmpty() -> {
                        FaqListCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("'$searchQuery' 관련 질문이 없습니다", color = Color.Gray)
                                Text("💡 이런 질문은 어떠세요?", color = Color.White)
                                listOf(
                                    "알람이 안 울려요", "수면 기록이 안 돼요", "권한 설정 방법"
                                ).forEach {
                                    Text("• $it", color = Color.LightGray)
                                }
                            }
                        }
                    }

                    else -> {
                        FaqListCard {
                            filteredItems.forEach { item ->
                                Column {
                                    Text("Q. ${item.question}", color = Color.White)
                                    Spacer(Modifier.height(6.dp))
                                    Text(item.answer, color = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }

            if (mainTab == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1:1 문의하기", color = Color.White)
                        Text(">", color = Color.Gray)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("피드백 보내기", color = Color.White)
                        Text(">", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun MainTabRow(menus: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val tabWidth = maxWidth / menus.size
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                menus.forEachIndexed { index, menu ->
                    Text(
                        modifier = Modifier
                            .width(tabWidth)
                            .clickable { onSelected(index) }
                            .padding(vertical = 8.dp),
                        text = menu,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyHighlight,
                        color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(4.dp)
                        .offset(x = tabWidth * selectedIndex)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun FaqListCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Preview
@Composable
fun SupportScreenPreview() {
    SleepAppTheme {
        SupportContent(
            mainTab = 0,
            searchQuery = "",
            allItems = listOf(
                FaqItem("배터리 소모가 심한가요?", "최적화 기술을 사용하여 배터리 소모를 최소화했습니다.")
            ),
            onTabSelected = {},
            onSearchQueryChanged = {}
        )
    }
}
