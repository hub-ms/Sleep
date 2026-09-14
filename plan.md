# Renight-Level 수면 앱 완성 6주 로드맵 (진행률 55% 기반, 주 25시간)

## 1. 현재 프로젝트 진행률 및 진단
* **현재 진행률: 55%**
* **진단 기준:**
  * **완료된 부분:** KMP 멀티플랫폼 구조, MVI 아키텍처, Spring 백엔드 핵심 API(인증/기초 데이터), 수면설정·측정중·홈·리포트·도움말·마이페이지 화면 골격이 모두 존재. 클래스 불균형 처리(sqrt-inverse 가중치+focal loss), 시퀀스 후처리(mode_filter/viterbi_smooth), 예시 리포트 안내(PreviewOverlay), 오픈소스 라이선스 화면, 1:1상담 링크, 버그 a(달력 확장)·b(알람 30분 미만 오이동)는 **이미 구현/수정되어 있으나 "연결"만 안 되거나 검증만 필요한 상태**.
  * **신규로 드러난 치명적 격차(하향 재산정 이유):**
    1. 온디바이스 추론이 **클래스 수 불일치(학습 4클래스 vs 앱 `NUM_CLASSES=5`)**와 **컨텍스트 길이 불일치(모델은 60-epoch 시퀀스 요구, `SleepAnalyzer.kt`는 길이 1로 호출)**로 인해 실제 동작하지 않을 가능성이 높음.
    2. F1 측정 코드 자체가 없어(`classification_report` import만 있고 미사용) "모델 개선(F1≥0.75)" 목표를 판단할 방법이 없음.
    3. 리포트 0값 버그(c), 음악 재클릭 버그(d)가 미해결.
    4. 유료 구독/인앱결제, 채팅 실서버 연동, iOS Xcode 프로젝트, 테스트 인프라(전 디렉터리 0개 파일)가 **전부 처음부터 신규 구축**해야 하는 상태.
    5. 심박수 제거는 센서/집계/리포지토리/UI/ML스크립트 등 **8개 이상 파일에 걸친 구조 변경**이 필요.
  * **결론:** UI 화면 골격 자체는 60%에 근접하지만, "제품으로서의 완성도"(ML 신뢰성, 수익화, 멀티플랫폼, 테스트, 출시 준비) 격차가 커 전체 진행률을 55%로 재산정함.

---

## 2. 개발 일정 및 시간 배분 (주 6일, 총 25시간)
* **월요일 ~ 금요일:** 매일 4시간 (기존 3.5h → 4h, 평일 순수 개발시간 확대)
* **토요일:** 5시간 (기존 2.5h → 5h, 통합테스트·회귀검증·콘텐츠 작업·버퍼)
* **일요일:** 휴식

> 근거: 총 가용시간이 20h→25h(+25%)로 늘었는데, 이번 요구사항은 버그 검증·ML 정합성 확보·테스트 인프라 신규 구축처럼 "잘라서 하기 어려운" 통합/검증성 작업 비중이 크다. 늘어난 5h를 전부 토요일에 배치해 매주 결과물을 실제로 검증하고 다음 주로 리스크를 넘기지 않도록 설계했다.

### 2-1. 카테고리별 시간 배분 (총 150h)

| 카테고리 | 시간 | 비중 |
| :--- | :---: | :---: |
| 화면 UI 개선(설정/트래킹/홈/리포트/마이페이지/도움말, 페르소나 포함) | 35h | 23.3% |
| ML 신호처리·구조 고도화(신호품질/정규화/증강/후처리연결/다중모달/앙상블/파인튜닝) | 21h | 14.0% |
| 테스트 인프라 구축 + 기능/비기능 테스트 | 16h | 10.7% |
| 통합 QA/버퍼(매주 토요일 일부) | 11h | 7.3% |
| 미해결 버그 수정(c, d) + a·b 회귀검증 | 8h | 5.3% |
| ML 모델-앱 연동 버그 수정(클래스 수/컨텍스트 길이) | 8h | 5.3% |
| 심박수 데이터 수집 제거(전 레이어) | 8h | 5.3% |
| 유료 구독/인앱결제 구현 | 8h | 5.3% |
| 커스텀 1:1 채팅 실서버 연동 | 8h | 5.3% |
| 출시 준비(서명/R8/아이콘/스토어 등록) | 9h | 6.0% |
| ML 성능측정 인프라(F1 평가 스크립트) | 4h | 2.7% |
| 디자인 통일(공용 칩 컴포넌트 추출/적용) | 4h | 2.7% |
| 권한 최초실행 플로우 연결 | 4h | 2.7% |
| iOS 신규 프로젝트 구성 | 3h | 2.0% |
| 콘텐츠(약관/개인정보처리방침/음원크레딧) | 3h | 2.0% |
| **합계** | **150h** | **100%** |

---

## 3. AI 도구 활용 워크플로우 (참고)

| 도구 | 주 용도 |
| :--- | :--- |
| **Claude** | ML 파이프라인(평가 스크립트, 신호처리, 후처리 연결), 리포트/페르소나 로직, 복잡한 버그 근본원인 분석 |
| **Cursor AI** | shared 모듈 구조 변경(심박제거, 컴포넌트 분리), Android/iOS/JVM 동시 반영 |
| **GitHub Copilot** | UI 보일러플레이트, 테스트 코드 생성, 스토어 등록용 부수 작업 |

---

## 4. 기능 및 화면 개발 우선순위 (재분류)

1. **High (필수/출시 차단 이슈):**
   * 미해결 버그 c(리포트 0값)·d(음악 재클릭) + ML 모델-앱 연동 버그(클래스 불일치, 컨텍스트 길이 불일치) — 다른 모든 기능의 신뢰성을 좌우
   * ML 성능측정 인프라(F1 평가) — 목표(0.75) 달성 여부를 판단할 유일한 수단
   * 심박수 데이터 수집 제거(전 레이어) + ML 모델 채널 재설계·구조 개선(F1≥0.75)
   * 유료 구독/인앱결제 — 수익모델 핵심, 스토어 심사 정책과 직결
   * 테스트 인프라 구축(기능/비기능 테스트), 앱스토어 출시 준비(서명/R8/등록) — 출시 자체를 막는 항목
2. **Medium (사용성/완성도):**
   * 디자인 통일(공용 칩), 수면설정/측정중/홈/리포트(동물 페르소나 UI 포함)/마이페이지/도움말 화면 개선
   * 최초실행 권한 플로우 연결
   * 신호품질/정규화/데이터증강/후처리연결/다중모달/개인 파인튜닝/모델 앙상블/소음측정 정확도(F1 보조 개선 요소)
   * 커스텀 1:1 채팅 실서버 연동, iOS 신규 프로젝트 구성
   * 약관/개인정보처리방침 실제 콘텐츠화
3. **Low (완료 확인/경미한 작업):**
   * 오픈소스 라이선스 화면(이미 완성 — 확인만), 클래스 불균형 처리(이미 구현 — 확인만), 버그 a·b(이미 수정됨 — 회귀검증만), 음원 크레딧 placeholder 데이터 교체, 이메일 문의 기능 제거(대상 코드 부재로 추정 — 확인만)

---

## 5. 주차별/요일별 구체적인 작업 계획

### 1주차: 버그 정합성 확보 + ML 성능측정 인프라 구축 (목표 진행률: 63%)
* **월(4h):** 리포트 0값 버그(c) 근본 수정 — `ReportViewModel.refreshToLatestSession()`을 화면 재진입 시 재호출하도록 `AppScreens.kt`에 `LaunchedEffect` 연결, `AndroidTrackingManager.finish()`의 fire-and-forget 구조를 저장완료 콜백/suspend 기반으로 바꿔 레이스컨디션 제거 `[Claude]`
* **화(4h):** 음악 재클릭 선택해제 버그(d) 수정 — `MusicCompactCard`가 항상 `onTogglePlaying()`을 호출하는 분기 수정, `MusicViewModel`의 `MusicSelected(music==null)` no-op 제거; 버그 a(달력 확장)·b(30분 미만 시 알람→리포트 오이동) 회귀 검증 및 기록 `[Copilot]`
* **수(4h):** ML 모델-앱 연동 버그① — `SleepStageClassifier.kt`의 `NUM_CLASSES` 5→4 정합화, 라벨 매핑이 `ReportScreen`/`ChartUtil` 표시에 미치는 영향 점검 `[Claude]`
* **목(4h):** ML 모델-앱 연동 버그② — `SleepAnalyzer.kt`의 추론 컨텍스트 길이를 1→60-epoch 슬라이딩 버퍼 구조로 변경, 온디바이스 추론 예외 재현/해소 확인 `[Claude]`
* **금(4h):** ML 성능측정 인프라 신규 구축 — `evaluate.py` 작성(`classification_report`, confusion matrix, macro/weighted F1), 클래스 불균형 처리(sqrt-inverse 가중치+focal loss)는 기존 구현 확인만 `[Claude]`
* **토(5h):** 통합 — 버그 4건 시나리오 회귀 테스트(30분 미만 종료→리포트, 음악 재클릭, 최초실행 권한 미확인 상태) + 수정된 모델의 F1 베이스라인 최초 측정·기록, 주간 회고

### 2주차: 디자인 통일 & 수면설정/측정중/홈 화면 개선 (목표 진행률: 74%)
* **월(4h):** 디자인 시스템 통일 — `HomeScreen`의 `MusicBrowserSection` 칩 스타일(RoundedCornerShape 20dp, 36dp, border 없음, primary/White 5%)을 공용 컴포저블(`SelectableChip`류)로 추출 `[Cursor]`
* **화(4h):** 수면 설정 화면 — 스크롤피커 위 빠른 알람시각 칩(공용 칩 적용), 스마트알람 범위 선택 UI를 공용 칩으로 교체, 디버그용 border 제거, 스마트알람 On 시 칩 위 `__:__~__:__` 표시 `[Copilot]`
* **수(4h):** 측정 중 화면 — 타이머 아이콘 교체, 반복 아이콘/기능 잔존 코드 정리(존재 확인 후 제거), 뒤로가기 종료 다이얼로그를 수면시간 30분 미만 조건분기로 수정 `[Copilot]`
* **목(4h):** 최초실행 권한 플로우 — 완성돼 있으나 미연결된 `PermissionScreen`/`PermissionViewModel`을 `AppScreens.kt` 네비게이션 그래프에 연결, 최초실행 여부 판별/저장 로직 추가 `[Cursor]`
* **금(4h):** 홈 화면① — 수면음악 미선택 시 카드 콘텐츠 수정, 타이머/즐겨찾기 텍스트 제거, 타이머 아이콘 클릭 시 칩 리스트 우측 슬라이드 애니메이션(트래킹 화면의 `AnimatedVisibility`+공용 칩 재사용) `[Claude]`
* **토(5h):** 홈 화면② — 측정 시작 전 예상 수면시간 30분 미만 안내 다이얼로그(트래킹 화면 AlertDialog 패턴 재사용) + 이번 주(설정/트래킹/홈) 전체 통합 리그레션

### 3주차: 리포트/마이페이지/도움말 화면 & 콘텐츠 정비 (목표 진행률: 84%)
* **월(4h):** 리포트 화면 — `Calendar` 컴포저블을 `ReportContract.State` 강결합에서 분리해 재사용 가능한 공용 컴포넌트로 리팩터링 + 미니멀 디자인(notefolio 코알라 수면앱 캘린더 스타일) 적용 `[Claude]`
* **화(4h):** 마이페이지 — 분리된 Calendar 컴포넌트 적용, 기본 프로필 이미지 교체 `[Copilot]`
* **수(4h):** 리포트 화면 — 예시 리포트 안내문구(`PreviewOverlay`) 연결(이미 구현됨, 호출부만 추가), 수면 유형 동물 페르소나 매칭 규칙 설계(수면단계 비율 기반 분류 로직/데이터 모델) `[Claude]`
* **목(4h):** 리포트 화면 — 동물 페르소나 매칭 UI 구현(behance Rouzzy 스타일 일러스트/배지, 리소스 확보 포함), 리포트 화면 내 배치 `[Claude]`
* **금(4h):** 도움말 화면 — 검색창/전체카테고리 칩 제거, FAQ를 카테고리당 카드 1개로 재구성, 1:1상담 링크 연결 재검증(이미 연결됨 확인) `[Copilot]`
* **토(5h):** 콘텐츠 정비 — 음원크레딧 placeholder(SleepSoft/example.com 등) 실제 데이터로 교체, 이용약관/개인정보처리방침을 외부 URL 링크 대신 실제 앱 내용 기준으로 재작성 + 주간 통합 QA

### 4주차: 심박수 데이터 수집 제거 & ML 신호처리 고도화 (목표 진행률: 91%)
* **월(4h):** 심박수 제거① — `SensorBridge.kt`(android/ios/jvm)의 심박 관련 코드 제거, `SignalProcessor.kt`(BCG 추정) 축소/제거 `[Cursor]`
* **화(4h):** 심박수 제거② — `SleepMeasureManager.kt`/`AndroidTrackingManager.kt`/`EnvironmentFeature.kt`/`SleepMinuteAggregate.kt`/`SleepSessionRepositoryImpl.kt`/`SleepReportCalculator.kt`에서 심박 필드 제거, `ReportScreen`/`ReportViewModel`/`ChartUtil.kt`의 UI 표시 제거 `[Cursor]`
* **수(4h):** ML 데이터셋 채널 재설계(8→6채널, `build_dataset_hybrid.py`/`compute_stats.py`) + 소음측정 정확도 개선(`DEVICE_FREQ_CORRECTION_DB` 기기별 실측 보정 테이블화, `getBestSampleRate()` 동적 선택) `[Claude]`
* **목(4h):** 신호 품질 개선(밴드패스 필터·노이즈 제거 고도화) + 정규화 기법 개선(Welford z-score 고도화, robust/per-channel scaling) `[Claude]`
* **금(4h):** 데이터 증강(PSG 도메인 증강 신규 추가, Accel 기존 스케일+지터 보완) + 수동 특징 결합(`bandpower_proxy` 파이프라인 연결) + 다중 모달(MFCC placeholder 실제 구현) `[Claude]`
* **토(5h):** 시퀀스 후처리 연결 — `postprocess.py`의 mode_filter/viterbi_smooth(이미 구현됨)를 `train.py` 평가 경로 및 `SleepAnalyzer.kt` 온디바이스 추론 경로에 실제 연결 + 모델 앙상블 실험 + 재학습 후 `evaluate.py`로 F1 재측정(목표 0.75 대비 점검)

### 5주차: 유료 구독/결제, 채팅 실서버 연동, iOS 신규 프로젝트 (목표 진행률: 96%)
* **월(4h):** ML 마무리 — 개인 수면 데이터 파인튜닝 프로토타입(소량 사용자 데이터로 최종 레이어 미세조정) + F1 목표 최종 점검, 미달 시 후처리 파라미터/임계값 튜닝으로 보정 `[Claude]`
* **화(4h):** 유료 구독① — 구독 플랜 UI(SubscriptionScreen) 구현, Spring 백엔드 구독 상태 API 설계 `[Cursor]`
* **수(4h):** 유료 구독② — 인앱결제(Google Play Billing/StoreKit) 연동, 구매 검증 콜백, 로컬 상태관리(ViewModel/Repository) `[Cursor]`
* **목(4h):** 이메일 문의 기능 제거 확인/정리(고객문의용 이메일 코드 부재 추정 — 확인) + 채팅① — `CustomChatScreen`(현재 로컬 mock UI)을 실서버 연동으로 전환 설계(Firestore 실시간 채팅 또는 외부 SDK 선정), 서버 스키마/보안 규칙 설계 `[Claude]`
* **금(4h):** 채팅② — 실제 메시지 송수신/실시간 리스너 구현, 채팅 화면 마무리 `[Cursor]`
* **토(5h):** iOS 신규 프로젝트 구성 — Xcode 프로젝트 신규 생성, KMP `shared` 모듈 연동, 주요 화면 스모크 확인 + 이번 주 통합 QA

### 6주차: 테스트 인프라 구축 + QA + 출시 준비 (목표 진행률: 100%)
* **월(4h):** 테스트 인프라① — `commonTest`/`androidUnitTest` 골격 구축, 핵심 ViewModel(`ReportViewModel`/`MusicViewModel`/`AlarmViewModel`) 단위테스트 작성 `[Copilot]`
* **화(4h):** 테스트 인프라② — `androidInstrumentedTest`/`iosTest` 골격, Compose UI 스모크 테스트, Spring 컨트롤러/서비스 테스트 작성 `[Copilot]`
* **수(4h):** 기능 테스트 — 6주간 변경된 전 화면/버그 수정 항목 체크리스트 기반 전체 회귀, `evaluate.py` 최종 F1 리포트 산출
* **목(4h):** 비기능 테스트 — 배터리/메모리(장시간 트래킹), 오프라인/네트워크 단절, 성능(Recomposition, API 응답시간) 점검 및 수정
* **금(4h):** 출시 준비① — `signingConfigs`(릴리즈 서명) 구성, R8/Proguard minify 활성화 및 규칙 정리, adaptive icon/그래픽 리소스 제작
* **토(5h):** 출시 준비② — 스토어 등록용 스크린샷/설명 문구 작성, Google Play Console/App Store Connect 등록 및 심사 제출, 6주 로드맵 최종 회고

---

## 각주

1. **진행률 산정 기준:** 화면 단위 완성도(약 70~80%)와 "제품 신뢰성"(ML 정합성 60% 미만, 수익화/멀티플랫폼/테스트 0%) 두 축을 가중 평균해 55%로 산정. 이전 5주 계획서의 60%는 코드 상세 감사 이전 수치이며, 이번 조사에서 온디바이스 추론 미동작 가능성 등 치명적 이슈가 새로 발견되어 하향 조정함.
2. **F1 0.75 리스크 버퍼:** 4주차 토요일에 1차 재측정을 배치하고, 5주차 월요일을 "ML 마무리/미달 시 보정" 전용 슬롯으로 별도 확보해 모델 재학습이 예상보다 오래 걸리는 리스크에 대응함.
3. **이미 구현되어 "확인/연결"로 축소된 항목:** 버그 a(달력 확장)·b(알람 30분 미만 오이동), 클래스 불균형 처리, `postprocess.py`의 mode_filter/viterbi_smooth 로직 자체, `PreviewOverlay`, 오픈소스 라이선스 화면(AboutLibraries), 1:1상담 링크 연결. 이 항목들은 "신규 구현"이 아니라 "연결 또는 검증"으로만 시간 배정함.
4. **외부 의존성 사전 확보 필요:** Apple Developer 계정(iOS 프로젝트/App Store 등록), Google Play/App Store 인앱결제 상품 설정, 채팅 SDK 또는 Firestore 사용 결정, 동물 페르소나 일러스트 리소스는 5~6주차 작업 착수 전 별도로 준비되어 있어야 일정 지연을 막을 수 있음.
5. **심박수 제거 영향 파일:** `SensorBridge.kt`(android/ios/jvm), `SignalProcessor.kt`, `SleepMeasureManager.kt`, `AndroidTrackingManager.kt`, `EnvironmentFeature.kt`, `SleepAnalyzer.kt`, `SleepMinuteAggregate.kt`, `SleepSessionRepositoryImpl.kt`, `SleepReportCalculator.kt`, `ReportScreen.kt`/`ReportViewModel.kt`/`ChartUtil.kt`, `ml/script`의 데이터셋/통계 스크립트 전체.

---
**최종 업데이트:** 2026-09-12
**목표 기한:** 작성일 기준 6주 후 완료
