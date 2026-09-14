package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 버전 히스토리 한 건을 나타내는 모델.
 */
data class VersionEntry(
    val version: String,
    val date: String,
    val isCurrent: Boolean = false,
    val changes: List<String>
)

/**
 * 실제 프로젝트에서는 이 목록을 서버(원격 config) 또는 로컬 JSON/리소스에서 불러오도록
 * 교체하는 것을 권장합니다. 여기서는 예시 데이터를 하드코딩했습니다.
 */
private val sampleVersionHistory = listOf(
    VersionEntry(
        version = "1.0.4",
        date = "2026.09.02",
        isCurrent = true,
        changes = listOf(
            "수면 리포트 그래프 로딩 속도 개선",
            "알람 반복 요일 설정 버그 수정",
            "다크모드 대비 개선"
        )
    ),
    VersionEntry(
        version = "1.0.3",
        date = "2026.08.11",
        changes = listOf(
            "수면 유도음악 5종 추가",
            "야간 무음 모드 안정성 개선"
        )
    ),
    VersionEntry(
        version = "1.0.2",
        date = "2026.07.20",
        changes = listOf(
            "프리미엄 구독 결제 오류 수정",
            "알림 권한 안내 화면 추가"
        )
    ),
    VersionEntry(
        version = "1.0.0",
        date = "2026.06.15",
        changes = listOf("SleepyTime 첫 출시 🎉")
    )
)

/**
 * 버전 히스토리 / 업데이트 정보 화면 콘텐츠.
 * navigation.VersionHistoryScreen(Voyager Screen)에서 이 함수를 호출합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryContent(
    versions: List<VersionEntry> = sampleVersionHistory,
    onBackClick: () -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "버전 히스토리", color = Color.White, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            versions.forEach { entry ->
                VersionEntryCard(entry)
            }
        }
    }
}

@Composable
private fun VersionEntryCard(entry: VersionEntry) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "v${entry.version}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (entry.isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "현재 버전",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.weight(1f, fill = true))
                Text(
                    text = entry.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
            }
            Spacer(Modifier.height(12.dp))
            entry.changes.forEach { change ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                    Text(
                        text = change,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}