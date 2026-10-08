@file:OptIn(ExperimentalStdlibApi::class)

package com.soundsleeper.app.ui.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import com.soundsleeper.app.R
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.ui.theme.bodyHighlight
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle

// 안드로이드에서는 AboutLibraries Gradle 플러그인이 빌드 시 res/raw/aboutlibraries.json을 생성한다.
// 리소스를 이름으로 찾는 produceLibraries() 기본 오버로드는 릴리스 빌드의 R8 최적화/리소스 축소
// (isMinifyEnabled, isShrinkResources)에서 해당 raw 리소스가 제거되거나 이름이 바뀌면 목록이 비어버린다.
// 따라서 컴파일 시점에 ID가 확정되는 produceLibraries(resId) 오버로드를 사용한다.
// 로딩이 끝나기 전에는 libs가 null이며, 이때는 빈 목록을 그린다.
//
// 라이브러리가 제공하는 LibrariesContainer 대신 목록을 직접 그린다. 기본 UI는 행을 눌러야
// 액션이 펼쳐지는 데다(detailMode = Inline) 버튼 라벨이 영문 Source/Website/View license 라서,
// "누르지 않아도 링크가 보이게" 하려면 어차피 행을 통째로 대체해야 한다. 데이터 모델(Library)만
// 쓰면 앱 테마와 문구를 그대로 맞출 수 있다.
@Composable
actual fun LibraryListSection() {
    val libs by produceLibraries(R.raw.aboutlibraries)
    val libraries = libs?.libraries.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(libraries, key = { it.uniqueId + (it.artifactVersion ?: "") }) { library ->
            LibraryRow(library)
        }
    }
}

@Composable
@ExperimentalStdlibApi
private fun LibraryRow(library: Library) {
    val uriHandler = LocalUriHandler.current
    val author = library.developers.mapNotNull { it.name }.filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { library.organization?.name.orEmpty() }
    val licenseNames = library.licenses.joinToString(", ") { it.spdxId ?: it.name }
    var showLicenseDetail by remember { mutableStateOf(false) }

    if (showLicenseDetail) {
        LibraryLicenseDialog(library = library, onDismiss = { showLicenseDetail = false })
    }

    SettingCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLicenseDetail = true }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = library.name,
                    style = MaterialTheme.typography.bodyHighlight,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                library.artifactVersion?.let { version ->
                    Text(
                        text = version,
                        style = MaterialTheme.typography.caption,
                        color = primary
                    )
                }
            }

            if (author.isNotBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.caption,
                    color = Color.White
                )
            }
            if (licenseNames.isNotBlank()) {
                Text(
                    text = licenseNames,
                    style = MaterialTheme.typography.caption,
                    color = Color.White
                )
            }

            // 링크는 접지 않고 항상 펼쳐 둔다. 주소가 없는 항목은 아예 그리지 않는다 —
            // 눌러도 아무 일이 없는 링크가 보이는 쪽이 더 나쁘다.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (library.strippedLicenseContent.isNotBlank()) {
                    LibraryLink("라이선스 전문") { showLicenseDetail = true }
                }
                library.scm?.url?.takeIf { it.isNotBlank() }?.let { url ->
                    LibraryLink("소스 코드") { uriHandler.openUri(url) }
                }
                library.website?.takeIf { it.isNotBlank() }?.let { url ->
                    LibraryLink("웹사이트") { uriHandler.openUri(url) }
                }
            }
        }
    }
}

@Composable
private fun LibraryLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        textDecoration = TextDecoration.Underline,
        color = primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

/** 라이브러리 행을 누르면 웹사이트로 나가는 대신 여기서 라이선스 전문을 바로 보여준다. */
@Composable
private fun LibraryLicenseDialog(library: Library, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = MaterialTheme.shapes.medium,
            color = surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.sectionTitle,
                    color = Color.White
                )
                library.artifactVersion?.let { version ->
                    Text(
                        text = version,
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
                Text(
                    text = library.strippedLicenseContent.ifBlank { "라이선스 전문이 없습니다." },
                    style = MaterialTheme.typography.caption,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
                TextButton(
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = onDismiss
                ) {
                    Text("닫기")
                }
            }
        }
    }
}
