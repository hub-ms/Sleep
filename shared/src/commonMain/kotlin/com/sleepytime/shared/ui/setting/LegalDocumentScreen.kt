package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption

// 💡 약관/개인정보처리방침 네이티브 화면 전환:
// 기존에는 AppInfoScreen에서 외부 URL(hub-ms.github.io/terms.html, privacy.html)을
// 브라우저로 여는 방식이었습니다. 저장소에 이미 있던 실제 문서(terms.html, privacy.html)의
// 내용을 그대로 옮겨 앱 내 네이티브 화면으로 렌더링합니다(오프라인에서도 확인 가능).

private data class LegalSection(
    val number: Int,
    val heading: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
    val tableRows: List<Pair<String, String>> = emptyList(),
    val callout: String? = null,
)

@Composable
private fun LegalDocumentContent(
    title: String,
    effectiveDate: String,
    footerNote: String,
    sections: List<LegalSection>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = effectiveDate,
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )
        }

        sections.forEach { section -> LegalSectionView(section) }

        Text(
            text = footerNote,
            style = MaterialTheme.typography.caption,
            color = Color.Gray
        )
    }
}

@Composable
private fun LegalSectionView(section: LegalSection) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = "${section.number}. ",
                style = MaterialTheme.typography.bodyHighlight,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = section.heading,
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White
            )
        }

        section.paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyText,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        if (section.bullets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                section.bullets.forEach { bullet ->
                    Row {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyText,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyText,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        if (section.tableRows.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    section.tableRows.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyText,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        section.callout?.let { callout ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Text(
                    text = callout,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun TermsContent() {
    LegalDocumentContent(
        title = "이용약관",
        effectiveDate = "시행일: 2026년 9월 8일 · 최종 수정일: 2026년 9월 8일",
        footerNote = "SleepyTime · 본 약관은 예시 템플릿이며 실제 서비스 배포 전 법률 전문가의 검토를 받으시기 바랍니다.",
        sections = listOf(
            LegalSection(
                1, "목적",
                paragraphs = listOf("이 약관은 SleepyTime(이하 \"회사\")이 제공하는 수면 관리 애플리케이션 및 관련 제반 서비스(이하 \"서비스\")의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.")
            ),
            LegalSection(
                2, "용어의 정의",
                bullets = listOf(
                    "이용자: 이 약관에 따라 회사가 제공하는 서비스를 이용하는 자",
                    "계정: 이용자의 식별과 서비스 이용을 위해 이용자가 설정한 정보",
                    "유료서비스: 회사가 유상으로 제공하는 구독형 프리미엄 기능(예: 알람음 확장, 수면 분석 리포트 등)",
                    "콘텐츠: 서비스 내에서 제공되는 수면 유도음, 알람음, 통계, 텍스트 등 일체의 정보"
                )
            ),
            LegalSection(
                3, "약관의 효력 및 변경",
                paragraphs = listOf("이 약관은 서비스 화면에 게시하거나 기타의 방법으로 이용자에게 공지함으로써 효력이 발생합니다. 회사는 관련 법령을 위배하지 않는 범위에서 이 약관을 변경할 수 있으며, 변경 시 적용일자 및 변경사유를 명시하여 최소 7일 전(이용자에게 불리한 변경의 경우 30일 전)에 공지합니다."),
                callout = "변경된 약관에 동의하지 않는 이용자는 서비스 이용을 중단하고 탈퇴할 수 있으며, 공지 후 이용자가 명시적으로 거부 의사를 표시하지 않고 서비스를 계속 이용하는 경우 약관 변경에 동의한 것으로 봅니다."
            ),
            LegalSection(
                4, "서비스의 제공 및 변경",
                paragraphs = listOf(
                    "회사는 다음과 같은 서비스를 제공합니다.",
                ),
                bullets = listOf(
                    "수면 기록 및 분석 기능",
                    "알람 및 수면 유도음 재생 기능",
                    "수면 리포트 및 통계 제공",
                    "기타 회사가 추가 개발하거나 제휴를 통해 제공하는 서비스"
                )
            ),
            LegalSection(
                5, "서비스 이용",
                paragraphs = listOf("서비스는 회사의 업무상 또는 기술상 특별한 지장이 없는 한 연중무휴, 1일 24시간 제공을 원칙으로 합니다. 다만 시스템 점검, 서버 증설 및 교체, 국가비상사태, 정전 등 불가피한 사유가 있는 경우 서비스 제공이 일시 중지될 수 있습니다.")
            ),
            LegalSection(
                6, "회원의 의무",
                paragraphs = listOf("이용자는 다음 행위를 하여서는 안 됩니다."),
                bullets = listOf(
                    "타인의 정보를 도용하는 행위",
                    "서비스를 이용하여 법령 또는 이 약관이 금지하는 행위를 하는 행위",
                    "회사의 서비스 운영을 방해하거나 서비스의 안정적 운영을 저해할 수 있는 정보 등을 전송하는 행위",
                    "회사가 제공하는 콘텐츠(음원 등)를 서비스 목적 외로 복제, 배포, 상업적으로 이용하는 행위"
                )
            ),
            LegalSection(
                7, "회사의 의무",
                paragraphs = listOf("회사는 관련 법령과 이 약관이 금지하거나 미풍양속에 반하는 행위를 하지 않으며, 계속적이고 안정적인 서비스 제공을 위해 최선을 다합니다. 회사는 이용자의 개인정보를 보호하기 위해 개인정보처리방침을 수립하고 이를 준수합니다.")
            ),
            LegalSection(
                8, "유료서비스 및 결제",
                paragraphs = listOf("유료서비스는 각 오픈마켓(Google Play, App Store 등)의 정책에 따라 결제되며, 구독형 상품은 이용자가 해지하지 않는 한 매 결제주기마다 자동으로 갱신됩니다. 구독 관리 및 해지는 이용자가 가입한 오픈마켓의 계정 설정 화면에서 직접 진행할 수 있습니다.")
            ),
            LegalSection(
                9, "청약철회 및 환불",
                paragraphs = listOf("유료서비스의 청약철회, 환불 절차는 관련 법령(전자상거래 등에서의 소비자보호에 관한 법률 등) 및 각 오픈마켓의 환불 정책을 따릅니다. 이용자는 오픈마켓의 결제 내역 화면을 통해 환불을 요청할 수 있습니다.")
            ),
            LegalSection(
                10, "계약 해지 및 이용 제한",
                paragraphs = listOf("이용자는 언제든지 앱 내 설정 메뉴를 통해 이용계약 해지(탈퇴)를 신청할 수 있습니다. 회사는 이용자가 이 약관의 의무를 위반한 경우 사전 통지 후 서비스 이용을 제한하거나 계약을 해지할 수 있습니다.")
            ),
            LegalSection(
                11, "면책조항",
                paragraphs = listOf("회사는 천재지변 또는 이에 준하는 불가항력으로 인해 서비스를 제공할 수 없는 경우 책임이 면제됩니다. 서비스에서 제공하는 수면 관련 정보 및 통계는 의학적 진단이나 처방을 대체하지 않으며, 건강에 관한 결정은 반드시 전문의와 상담하시기 바랍니다.")
            ),
            LegalSection(
                12, "분쟁 해결 및 준거법",
                paragraphs = listOf("이 약관과 관련하여 회사와 이용자 간 분쟁이 발생한 경우 상호 협의하여 원만히 해결하는 것을 원칙으로 하며, 협의가 이루어지지 않을 경우 민사소송법상의 관할법원에 소를 제기할 수 있습니다. 이 약관은 대한민국 법령에 따라 규율되고 해석됩니다.")
            ),
        )
    )
}

@Composable
fun PrivacyPolicyContent() {
    LegalDocumentContent(
        title = "개인정보 처리방침",
        effectiveDate = "시행일: 2026년 9월 8일 · 최종 수정일: 2026년 9월 8일",
        footerNote = "SleepyTime · 본 방침은 예시 템플릿이며, 실제 수집 항목·위탁업체·보관기간은 서비스 실제 운영 내용에 맞게 수정하고 법률 전문가의 검토를 받으시기 바랍니다.",
        sections = listOf(
            LegalSection(
                1, "수집하는 개인정보 항목 및 수집방법",
                paragraphs = listOf("SleepyTime(이하 \"회사\")은 서비스 제공을 위해 다음과 같은 개인정보를 수집합니다."),
                tableRows = listOf(
                    "필수 (회원가입 시)" to "이메일 주소, 비밀번호(또는 소셜 로그인 식별값), 기기 식별자(디바이스 ID)",
                    "서비스 이용 과정에서 자동 생성" to "수면 시작·종료 시각, 수면 패턴 데이터, 알람 설정값, 앱 사용(접속) 기록, OS 종류 및 버전, 기기 모델명, 광고식별자, 푸시 알림 토큰",
                    "유료서비스 이용 시" to "구독 상품명, 결제일, 결제수단 정보(오픈마켓을 통해 처리되며 카드번호 등 결제 상세정보는 회사가 직접 저장하지 않음)",
                    "선택" to "닉네임, 프로필 이미지, 시간대(위치정보 기반 자동알람 설정 시)"
                )
            ),
            LegalSection(
                2, "개인정보의 수집 및 이용목적",
                bullets = listOf(
                    "회원 식별 및 서비스 부정이용 방지",
                    "수면 기록 저장, 분석 및 맞춤형 리포트 제공",
                    "알람 및 푸시 알림 서비스 제공",
                    "유료서비스 결제 및 구독 관리",
                    "고객 문의 응대 및 공지사항 전달",
                    "서비스 개선을 위한 통계 분석 (비식별 처리 후 활용)"
                )
            ),
            LegalSection(
                3, "개인정보의 보유 및 이용기간",
                paragraphs = listOf("회사는 원칙적으로 개인정보 수집 및 이용목적이 달성된 후에는 해당 정보를 지체 없이 파기합니다. 단, 관계 법령에 따라 보존할 필요가 있는 경우 회사는 아래와 같이 관계 법령에서 정한 일정한 기간 동안 회원정보를 보관합니다."),
                tableRows = listOf(
                    "계약 또는 청약철회 등에 관한 기록" to "5년 (전자상거래 등에서의 소비자보호에 관한 법률)",
                    "대금결제 및 재화 등의 공급에 관한 기록" to "5년 (전자상거래 등에서의 소비자보호에 관한 법률)",
                    "소비자의 불만 또는 분쟁처리에 관한 기록" to "3년 (전자상거래 등에서의 소비자보호에 관한 법률)",
                    "웹사이트 방문(접속) 기록" to "3개월 (통신비밀보호법)"
                )
            ),
            LegalSection(
                4, "개인정보의 제3자 제공",
                paragraphs = listOf("회사는 이용자의 개인정보를 원칙적으로 외부에 제공하지 않습니다. 다만 아래의 경우에는 예외로 합니다."),
                bullets = listOf(
                    "이용자가 사전에 별도로 동의한 경우",
                    "법령의 규정에 의거하거나, 수사 목적으로 법령에 정해진 절차와 방법에 따라 수사기관의 요구가 있는 경우"
                )
            ),
            LegalSection(
                5, "개인정보 처리의 위탁",
                paragraphs = listOf("회사는 원활한 서비스 제공을 위해 아래와 같이 개인정보 처리업무를 외부 업체에 위탁하고 있으며, 관계 법령에 따라 위탁계약 시 개인정보가 안전하게 관리될 수 있도록 필요한 사항을 규정합니다."),
                tableRows = listOf(
                    "클라우드 인프라 사업자" to "서버 호스팅 및 데이터 저장",
                    "푸시 알림 서비스 제공업체" to "알람 및 공지 푸시 발송",
                    "결제(오픈마켓) 사업자" to "구독 결제 처리",
                    "고객상담 도구 제공업체" to "고객 문의 접수 및 응대"
                ),
                callout = "실제 배포 시 위 표를 실제 사용 중인 업체명(예: AWS, Firebase Cloud Messaging, Google Play/App Store 등)으로 구체적으로 명시해야 합니다."
            ),
            LegalSection(
                6, "이용자의 권리와 행사방법",
                paragraphs = listOf("이용자는 언제든지 자신의 개인정보를 조회하거나 수정할 수 있으며, 회원 탈퇴를 통해 수집·이용 동의를 철회할 수 있습니다. 개인정보 조회, 수정, 삭제, 처리정지 요청은 앱 내 설정 > 계정 관리 메뉴를 통해 직접 처리하거나, 아래 개인정보 보호책임자에게 서면, 전화 또는 이메일로 연락하시면 지체 없이 조치합니다.")
            ),
            LegalSection(
                7, "개인정보의 파기절차 및 방법",
                paragraphs = listOf("이용자가 입력한 정보는 목적 달성 후 별도의 DB(종이의 경우 별도의 서류함)로 옮겨져 내부 방침 및 관계 법령에 따라 일정 기간 저장된 후 혹은 즉시 파기됩니다. 전자적 파일 형태의 정보는 기록을 재생할 수 없는 기술적 방법을 사용하여 삭제하며, 종이에 출력된 개인정보는 분쇄기로 분쇄하거나 소각을 통하여 파기합니다.")
            ),
            LegalSection(
                8, "자동 수집 장치의 설치·운영 및 거부",
                paragraphs = listOf("회사는 이용 패턴 분석 및 서비스 개선을 위해 앱 분석 도구(SDK)를 통한 자동 수집 장치를 운영할 수 있습니다. 이용자는 기기의 설정 메뉴에서 광고 식별자 초기화 또는 맞춤 광고 수신 거부를 선택할 수 있으며, 알림 수신은 앱 내 설정 또는 기기의 알림 설정에서 언제든지 거부할 수 있습니다.")
            ),
            LegalSection(
                9, "개인정보의 안전성 확보조치",
                paragraphs = listOf("회사는 개인정보보호법 제29조에 따라 다음과 같은 안전성 확보 조치를 취하고 있습니다."),
                bullets = listOf(
                    "비밀번호 등 중요 정보의 암호화 저장 및 전송",
                    "해킹 등에 대비한 접근 통제 시스템 설치 및 보안 프로그램 운영",
                    "개인정보 접근 권한의 최소화 및 접근 기록 보관",
                    "개인정보 취급 직원의 최소화 및 정기 교육"
                )
            ),
            LegalSection(
                10, "개인정보 보호책임자",
                paragraphs = listOf("회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와 관련한 이용자의 불만처리 및 피해구제 등을 위하여 아래와 같이 개인정보 보호책임자를 지정하고 있습니다."),
                tableRows = listOf(
                    "개인정보 보호책임자" to "이름 및 직책 입력 예정",
                    "이메일" to "privacy@sleepytime.app",
                    "연락처" to "연락처 정보 입력 예정"
                ),
                callout = "기타 개인정보침해에 대한 신고나 상담이 필요하신 경우 개인정보침해신고센터(privacy.kisa.or.kr / 국번없이 118), 대검찰청 사이버수사과(spo.go.kr / 국번없이 1301), 경찰청 사이버수사국(ecrm.police.go.kr / 국번없이 182)으로 문의하실 수 있습니다."
            ),
            LegalSection(
                11, "고지의 의무",
                paragraphs = listOf("현 개인정보 처리방침 내용 추가, 삭제 및 수정이 있을 경우 개정 최소 7일 전부터 앱 공지사항 또는 별도의 알림을 통해 고지할 것입니다. 다만 이용자 권리의 중대한 변경이 있을 경우에는 최소 30일 전에 고지합니다.")
            ),
        )
    )
}
