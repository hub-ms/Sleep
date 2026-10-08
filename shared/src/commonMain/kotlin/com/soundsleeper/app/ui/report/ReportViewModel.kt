
package com.soundsleeper.app.ui.report

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.soundsleeper.app.domain.model.SleepSession
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.util.SleepAdviceAnalyzer
import com.soundsleeper.app.domain.repository.SleepAdviceRepository
import com.soundsleeper.app.domain.repository.AuthRepository
import com.soundsleeper.app.domain.repository.SleepSessionRepository
import com.soundsleeper.app.ui.auth.AuthContract
import com.soundsleeper.app.util.IdGenerator.generateSessionId
import com.soundsleeper.app.util.SleepSessionUtil.toReportData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlin.collections.emptyList
import kotlin.time.Duration.Companion.milliseconds

/**
 * 리포트 화면(수면 사이클의 마지막 화면)의 ViewModel. 측정이 끝나면 [loadSession]으로
 * 그 세션을 바로 열거나, 일반 진입 시 [refreshToLatestSession]으로 최신 세션을 불러오며,
 * 날짜 이동/삭제/AI 조언 요청 등 리포트 화면의 모든 상호작용을 처리한다.
 */
@ExperimentalTime
class ReportViewModel(
    private val sleepSessionRepository: SleepSessionRepository,
    private val authRepository: AuthRepository,
    private val sleepAdviceRepository: SleepAdviceRepository,
)  : AppScopedScreenModel() {
    private val initialDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val _state = MutableStateFlow(
        ReportContract.State(
            reportData = DemoReportFactory.createPreviewData(0L, initialDate),
            weeklyChartData = DemoReportFactory.createPreviewData(0L, initialDate)
        )
    )
    val state = _state.asStateFlow()

    private val _authState = MutableStateFlow(AuthContract.State())

    private val currentUser: User?
        get() = _authState.value.user

    private val _intentChannel = Channel<ReportContract.Intent>(Channel.BUFFERED)

    /** Intent 처리 루프를 걸고, 현재 사용자 정보를 읽은 뒤 최신 세션으로 초기 데이터를 불러온다. */
    init {
        modelScope.launch {
            launch {
                _intentChannel.receiveAsFlow().collect { processIntent(it) }
            }

            val user = authRepository.getUser()
            user?.let { actualUser ->
                _authState.update { it.copy(user = actualUser) }
            }

            // 최초 진입 시 가장 최신 세션의 날짜로 이동해서 데이터를 불러옵니다.
            refreshToLatestSession()
        }
    }

    /** 화면에서 올라오는 Intent를 채널에 넣는다(실제 처리는 [processIntent]가 순차적으로 담당). */
    fun sendIntent(intent: ReportContract.Intent) {
        modelScope.launch {
            _intentChannel.send(intent)
        }
    }

    // 🐛 버그 수정 (수면 종료 후 리포트 화면에 새 데이터가 바로 안 뜨던 문제):
    // ReportViewModel은 화면을 벗어났다가 다시 돌아와도(예: 홈 -> 측정 종료 -> 리포트) 보통 재생성되지 않고
    // 계속 살아있는 ScreenModel입니다. 그런데 최신 세션 조회는 기존에 init{} 블록 안에서 딱 한 번만
    // 실행됐기 때문에, 측정이 끝난 직후 리포트 화면으로 이동해도 방금 저장된 새 세션이 반영되지 않고
    // 이전에 로드했던(오래된) 상태가 계속 보였습니다. 앱을 완전히 종료했다가 재실행해야만 ViewModel이
    // 새로 생성되면서 init{}이 다시 실행되어 최신 세션이 보였던 것이 원인입니다.
    //
    // 해결: 최신 세션을 조회해서 그 날짜로 데이터를 불러오는 로직을 재사용 가능한 함수로 분리했습니다.
    // 리포트 화면이 다시 화면에 나타날 때마다(onResume 시점) 이 함수를 호출해주면 됩니다. 예)
    //
    //   val screenModel = rememberScreenModel { ReportViewModel(...) }
    //   LaunchedEffect(Unit) { screenModel.refreshToLatestSession() }
    //
    // LaunchedEffect(Unit)은 이 Screen의 Content()가 컴포지션에 새로 들어올 때마다(= 화면에 다시
    // 진입할 때마다) 실행되므로, 수면 측정을 마치고 리포트 화면으로 넘어오는 순간 항상 최신 세션을
    // 다시 조회하게 되어 즉시 반영됩니다.
    fun refreshToLatestSession() {
        modelScope.launch {
            val latestSession = sleepSessionRepository.getLatestSession()
            // 이 조회 결과의 null 여부가 곧 "한 번이라도 측정한 적이 있는가"다. 예전에는 날짜만 쓰고
            // 버렸는데, 리포트가 데모 데이터인지 알리려면 이 사실이 화면까지 가야 한다.
            _state.update { it.copy(hasAnySession = latestSession != null) }
            val latestDate = latestSession?.date ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
            loadData(latestDate)
        }
    }

    // 측정을 막 끝내고 넘어온 경우에는 "지금 가장 최근 세션"을 추측하지 말고, 방금 끝난 세션을
    // ID로 직접 연다. 예전에는 HomeScreen이 sessionId를 인자로 받고도 쓰지 않아서 항상
    // refreshToLatestSession()으로 떨어졌는데, 저장 커밋이 늦으면 조회가 비어 미리보기가 떴다.
    //
    // 저장은 finish()가 fire-and-forget으로 띄우는 서비스에서 일어나므로 화면 진입 시점에
    // 아직 커밋 전일 수 있다. 짧게 재시도해서 그 레이스를 흡수하고, 그래도 없으면 그때
    // 기존 경로로 물러난다.
    fun loadSession(sessionId: String) {
        modelScope.launch {
            // 기다리는 동안에는 미리보기 상태를 확정하지 않는다. 예전에는 재시도 예산이 1초뿐이라
            // 분석이 끝나기 전에 hasAnySession=false 가 박히고 데모 리포트가 떠버렸는데, 세션
            // 테이블을 Flow 로 보고 있지 않아서 뒤늦게 저장이 끝나도 다시 조회하지 않았다.
            _state.update { it.copy(isAwaitingSession = true, sessionUnavailable = false) }

            var session = sleepSessionRepository.getSessionById(sessionId)
            Napier.d("sessionId=${sessionId}, session=$session")
            var attempt = 0
            while (session == null && attempt < SESSION_LOOKUP_RETRIES) {
                delay(SESSION_LOOKUP_RETRY_DELAY_MS.milliseconds)
                session = sleepSessionRepository.getSessionById(sessionId)
                attempt++
            }

            if (session == null) {
                Napier.w("세션($sessionId)을 찾지 못해 최신 세션으로 대체합니다.")
                _state.update { it.copy(isAwaitingSession = false, sessionUnavailable = true) }
                refreshToLatestSession()
                return@launch
            }

            _state.update {
                it.copy(hasAnySession = true, isAwaitingSession = false, sessionUnavailable = false)
            }
            loadData(session.date)
        }
    }

    /** [sendIntent]로 들어온 리포트 화면 Intent(날짜 선택, 캘린더 토글, 이동, 삭제)를 분기 처리한다. */
    private suspend fun processIntent(intent: ReportContract.Intent) {
        when (intent) {
            is ReportContract.Intent.SelectDate -> {
                _state.update { it.copy(isCalendarExpanded = false) }
                loadData(intent.date)
            }
            is ReportContract.Intent.ToggleCalendar -> {
                _state.update { it.copy(isCalendarExpanded = intent.isExpanded) }
            }
            is ReportContract.Intent.PrevClicked -> handleNavigation(isNext = false, unit = intent.unit)
            is ReportContract.Intent.NextClicked -> handleNavigation(isNext = true, unit = intent.unit)
            is ReportContract.Intent.DeleteReport -> {
                _state.update { it.copy(showDeleteDialog = true, pendingDeleteSessionId = intent.sessionId) }
            }
            is ReportContract.Intent.ConfirmDelete -> {
                sleepSessionRepository.deleteSession(sessionId = intent.sessionId)
                // 마지막 기록을 지웠다면 다시 데모 데이터 안내를 띄워야 한다.
                val stillHasSession = sleepSessionRepository.getLatestSession() != null
                _state.update {
                    it.copy(
                        showDeleteDialog = false,
                        pendingDeleteSessionId = null,
                        hasAnySession = stillHasSession
                    )
                }
                loadData(_state.value.date)
            }
            is ReportContract.Intent.DismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, pendingDeleteSessionId = null) }
            }
        }
    }

    private fun handleNavigation(isNext: Boolean, unit: DateTimeUnit.DateBased) {
        // 월 단위 이동은 화면에 보이는 달력 격자와 1:1로 맞춰 날짜 연산으로 처리한다.
        // 공용 상태(isCalendarExpanded) 대신 전달받은 unit 을 보는 이유는, 홈 화면 달력이
        // 공용 플래그를 켜지 않은 채 항상 월간으로 표시되기 때문이다. 플래그를 기준으로 삼으면
        // 홈에서 "1달 전"을 눌렀는데 실제로는 기록이 있는 가장 가까운 날로 점프해 버린다.
        if (_state.value.isPreview || unit is DateTimeUnit.MonthBased) {
            val newDate = if (isNext) _state.value.date.plus(1, unit)
            else _state.value.date.minus(1, unit)
            loadData(newDate)
        } else {
            modelScope.launch {
                val currentDate = _state.value.date
                val targetDate = if (isNext) {
                    sleepSessionRepository.getSessionByDateRange(
                        currentDate.plus(1, DateTimeUnit.DAY),
                        LocalDate(2100, 12, 31)
                    ).minByOrNull { it.date }?.date
                } else {
                    sleepSessionRepository.getSessionByDateRange(
                        LocalDate(1900, 1, 1),
                        currentDate.minus(1, DateTimeUnit.DAY)
                    ).maxByOrNull { it.date }?.date
                }

                if (targetDate != null) {
                    loadData(targetDate)
                }
            }
        }
    }

    /**
     * 선택된 날짜의 리포트를 구성하는 핵심 함수. 그 날의 세션, 전날 데이터, 이번 주/저번 주
     * 주간 집계를 모두 모아 state에 반영한다. 해당 월에 세션이 전혀 없으면 [loadPreviewReport]로
     * 데모 데이터를 보여준다.
     */
    private fun loadData(date: LocalDate) {
        loadRecentAverage(date)
        modelScope.launch {
            _state.update { it.copy(date = date) }

            val (monthSessionsMap, sessionDates) = getMonthSessionsAndDates(date)

            if (monthSessionsMap.isEmpty()) {
                loadPreviewReport(date)
                return@launch
            }

            val prevDate = date.minus(1, DateTimeUnit.DAY)
            val prevSession = sleepSessionRepository.getSessionByDate(prevDate)
            val prevDayData = prevSession?.toReportData()

            val monday = date.minus((date.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)
            val prevMonday = monday.minus(7, DateTimeUnit.DAY)

            val (currentWeekData, curSessions) = loadPeriodReportData(monday, 7)
            val (prevWeekData, prevSessions) = loadPeriodReportData(prevMonday, 7)

            val finalCurrentWeekData = if (curSessions.isEmpty()) null else currentWeekData
            val finalPrevWeekData = if (prevSessions.isEmpty()) null else prevWeekData

            val session = monthSessionsMap[date]
            val dayData = session?.toReportData()
            val monthScores = monthSessionsMap.mapValues { (_, s) -> s.sleepEfficiency }

            if (session == null) {
                _state.update {
                    it.copy(
                        date = date,
                        reportData = dayData,
                        weeklyChartData = finalCurrentWeekData,
                        prevWeeklyChartData = finalPrevWeekData,
                        prevDayReportData = prevDayData,
                        isPreview = false,
                        sessionDates = sessionDates
                    )
                }
            } else {
                val bedTime = date.atTime(
                    dayData?.bedTime?.hour ?: DEFAULT_BED_HOUR,
                    dayData?.bedTime?.minute ?: DEFAULT_BED_MINUTE,
                )
                val totalMin = session.duration.awakeMinutes + session.duration.lightMinutes +
                        session.duration.deepMinutes + session.duration.remMinutes
                val wakeTime = bedTime
                    .toInstant(TimeZone.currentSystemDefault())
                    .plus(totalMin.toLong(), DateTimeUnit.MINUTE)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val sleepMinutes = session.duration.lightMinutes + session.duration.deepMinutes + session.duration.remMinutes


                _state.update {
                    it.copy(
                        date = date,
                        reportData = session.toReportData().copy(
                            dailyBedTimes = mapOf(date to bedTime),
                            dailyWakeTimes = mapOf(date to wakeTime),
                            dailySleepMinutes = mapOf(date to sleepMinutes),
                            dailyScores = monthScores + mapOf(date to session.sleepEfficiency),
                            dailySleepLatencyMinutes = mapOf(date to session.duration.sleepLatencyMinutes),
                            dailyAvgNoises = mapOf(date to session.environment.stats.noise.avg),
                        ),
                        weeklyChartData = finalCurrentWeekData ?: session.toReportData(),
                        prevWeeklyChartData = finalPrevWeekData,
                        prevDayReportData = prevDayData,
                        isPreview = false,
                        sessionDates = sessionDates
                    )
                }
            }
        }
    }
    /** 실제 세션이 없는 사용자/기간을 위해 [DemoReportFactory]로 데모 리포트 데이터를 만들어 보여준다. */
    private fun loadPreviewReport(date: LocalDate) {
        val userId = currentUser?.userId ?: 0L
        val firstDayOfMonth = LocalDate(date.year, date.month, 1)

        val extendedStartDate = firstDayOfMonth.minus(1, DateTimeUnit.MONTH)
        val targetMonthDates = (0 until 90).map { offset ->
            extendedStartDate.plus(offset, DateTimeUnit.DAY)
        }

        val rangePreviewReport = DemoReportFactory.createRangePreviewData(
            userId = userId,
            dates = targetMonthDates
        )

        val selectedDayReport = DemoReportFactory.createPreviewData(userId, date)
        val finalPreviewReport = selectedDayReport.copy(
            dailyBedTimes = rangePreviewReport.dailyBedTimes,
            dailyWakeTimes = rangePreviewReport.dailyWakeTimes,
            dailySleepMinutes = rangePreviewReport.dailySleepMinutes,
            dailyScores = rangePreviewReport.dailyScores,
            dailySleepLatencyMinutes = rangePreviewReport.dailySleepLatencyMinutes,
            dailyAvgNoises = rangePreviewReport.dailyAvgNoises
        )

        val monday = date.minus((date.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)
        val targetWeekDates = (0 until 7).map { monday.plus(it, DateTimeUnit.DAY) }
        val previewWeekData = DemoReportFactory.createRangePreviewData(userId, targetWeekDates)

        _state.update {
            it.copy(
                date = date,
                reportData = finalPreviewReport,
                weeklyChartData = previewWeekData,
                isPreview = true,
                sessionDates = targetMonthDates.toSet()
            )
        }
    }
    /** 캘린더 표시 범위(해당 월 전후 포함)의 세션들을 날짜별 맵과 "세션이 있는 날짜" 집합으로 가져온다. */
    private suspend fun getMonthSessionsAndDates(date: LocalDate): Pair<Map<LocalDate, SleepSession>, Set<LocalDate>> {
        val firstDay = LocalDate(date.year, date.month, 1).minus(15, DateTimeUnit.DAY)
        val lastDay = LocalDate(date.year, date.month, 1).plus(2, DateTimeUnit.MONTH)

        val sessions = sleepSessionRepository.getSessionByDateRange(firstDay, lastDay)

        val sessionsMap = sessions.associateBy { it.date }
        val sessionDates = sessionsMap.keys

        return Pair(sessionsMap, sessionDates)
    }

    /**
     * "나의 최근 수면과 비교"에 쓸 최근 평균을 채운다.
     *
     * 집계를 새로 짜지 않고 주간 카드가 이미 쓰는 loadPeriodReportData 를 그대로 재사용한다.
     * 같은 계산을 두 벌 두면 한쪽만 고쳐져 값이 어긋나기 쉽다.
     */
    private fun loadRecentAverage(date: LocalDate) {
        modelScope.launch {
            val start = date.minus(RECENT_AVERAGE_DAYS, DateTimeUnit.DAY)
            val (average, sessions) = loadPeriodReportData(start, RECENT_AVERAGE_DAYS)
            _state.update {
                it.copy(
                    recentAverage = if (sessions.isEmpty()) null else average,
                    recentSampleCount = sessions.size
                )
            }
        }
    }

    /**
     * AI 수면 개선 조언을 불러온다.
     *
     * 판정(규칙 엔진)은 기기에서 즉시 끝나므로 먼저 그 문구로 채워 두고, 서버가 다듬은
     * 문장이 오면 갈아 끼운다. 이렇게 하면 네트워크가 느리거나 끊겨도 빈 화면이 남지 않는다.
     *
     * 프리미엄이 아니면 호출하지 않는다. 화면에서 잠금 카드로 가려 놓고 요청만 계속 나가면
     * 보이지도 않는 응답에 LLM 비용이 든다.
     */
    fun loadSleepAdvice(isUserPremium: Boolean) {
        val data = _state.value.reportData
        if (!isUserPremium || data == null || _state.value.isPreview) {
            _state.update { it.copy(sleepAdvice = ReportContract.SleepAdviceUiState.Idle) }
            return
        }

        modelScope.launch {
            val findings = SleepAdviceAnalyzer.analyze(data)
            val localText = findings.map { it.localizedFallbackText() }.joinToString(separator = "\n\n")
            _state.update {
                it.copy(sleepAdvice = ReportContract.SleepAdviceUiState.Fallback(localText))
            }

            val advice = sleepAdviceRepository.getAdvice(data.sessionId, findings)
            _state.update {
                it.copy(
                    sleepAdvice = if (advice.isFromServer) {
                        ReportContract.SleepAdviceUiState.Loaded(advice.text)
                    } else {
                        ReportContract.SleepAdviceUiState.Fallback(advice.text)
                    }
                )
            }
        }
    }

    /**
     * 주어진 기간(startDate부터 totalDays일)의 모든 세션을 합산/평균해 하나의 [ReportContract.ReportData]로
     * 만든다. 주간 카드, 저번 주 비교, 최근 N일 평균이 모두 이 함수를 공유한다.
     * 해당 기간에 세션이 전혀 없으면 기본값(0)으로 채운 빈 데이터를 반환한다.
     */
    private suspend fun loadPeriodReportData(
        startDate: LocalDate,
        totalDays: Int
    ): Pair<ReportContract.ReportData, List<SleepSession>> {
        val endDate = startDate.plus(totalDays, DateTimeUnit.DAY)
        val sessions = sleepSessionRepository.getSessionByDateRange(startDate, endDate)
        val targetDays = (0 until totalDays).map { startDate.plus(it, DateTimeUnit.DAY) }

        if (sessions.isEmpty()) {
            return Pair(
                ReportContract.ReportData(
                    sessionId = "",
                    bedTime = startDate.atTime(DEFAULT_BED_HOUR, DEFAULT_BED_MINUTE),
                    wakeTime = startDate.plus(1, DateTimeUnit.DAY)
                        .atTime(DEFAULT_WAKE_HOUR, DEFAULT_WAKE_MINUTE),
                    sleepScore = 0,
                    sleepLatencyMinutes = 0.0,

                    environmentHistory = emptyList(),
                    awakeMinutes = 0.0,
                    lightMinutes = 0.0,
                    deepMinutes = 0.0,
                    remMinutes = 0.0,
                    sleepMinutes = 0.0,
                    targetMinutes = 0.0,
                    stageTimeline = emptyList(),
                    wakeCount = 0,

                    avgNoise = 0f,
                    isNoiseDanger = false,

                    dailyBedTimes = emptyMap(),
                    dailyWakeTimes = emptyMap(),
                    dailySleepMinutes = emptyMap(),
                    dailyScores = emptyMap(),
                    dailySleepLatencyMinutes = emptyMap(),
                    dailyWakeCounts = emptyMap(),

                    totalWakeCount = 0,
                    dailyAvgNoises = emptyMap(),
                ),
                sessions
            )
        }
        Napier.d("sessions=$sessions")
        val sessionReports = sessions.map { it to it.toReportData() }
        Napier.d("sessionReports=$sessionReports")

        val dailyBedTimesMap = sessionReports.associate { (session, report) ->
            session.date to report.bedTime
        }
        val dailyWakeTimesMap = sessionReports.associate { (session, report) ->
            session.date to report.wakeTime
        }

        val avgBedTime = sessionReports
            .map { it.second.bedTime }
            .let { times ->
                val avgMinutesFromBase = times.map { time ->
                    val totalMinutes = time.hour * 60 + time.minute
                    if (totalMinutes < 1080) totalMinutes + 1440 else totalMinutes
                }.average().toInt()

                val finalMinutes = (avgMinutesFromBase % 1440)
                val hour = finalMinutes / 60
                val minute = finalMinutes % 60

                LocalDateTime(startDate.year, startDate.month, startDate.day, hour, minute)
            }
        val avgWakeTime = sessionReports
            .map { it.second.wakeTime }
            .let { times ->
                val avgMinutesFromBase = times.map { time ->
                    val totalMinutes = time.hour * 60 + time.minute
                    if (totalMinutes < 720) totalMinutes + 1440 else totalMinutes
                }.average().toInt()

                val finalMinutes = (avgMinutesFromBase % 1440)
                val hour = finalMinutes / 60
                val minute = finalMinutes % 60

                LocalDateTime(startDate.year, startDate.month, startDate.day, hour, minute)
            }

        val userContext = authRepository.getUserContext()

        return Pair(
            ReportContract.ReportData(
                sessionId = generateSessionId(user = userContext),
                bedTime = avgBedTime,
                wakeTime = avgWakeTime,
                sleepScore = sessions.map { it.sleepEfficiency }.average().toInt(),
                sleepLatencyMinutes = sessions.map { it.duration.sleepLatencyMinutes }.average(),

                environmentHistory = sessions.flatMap { it.environment.history },
                awakeMinutes = sessions.sumOf { it.duration.awakeMinutes },
                lightMinutes = sessions.sumOf { it.duration.lightMinutes },
                deepMinutes = sessions.sumOf { it.duration.deepMinutes },
                remMinutes = sessions.sumOf { it.duration.remMinutes },
                sleepMinutes = sessions.sumOf { it.duration.lightMinutes + it.duration.deepMinutes + it.duration.remMinutes },
                targetMinutes = sessions.sumOf { it.duration.targetMinutes },
                stageTimeline = sessions.flatMap { it.stageTimeline },

                wakeCount = sessions.sumOf { it.wakeCount },

                avgNoise = sessions.map { it.environment.stats.noise.avg }.average().toFloat(),
                isNoiseDanger = sessions.any { it.environment.flags.isNoiseDanger },

                dailyBedTimes = dailyBedTimesMap,
                dailyWakeTimes = dailyWakeTimesMap,
                dailySleepMinutes = targetDays.mapNotNull { date ->
                    sessions.find { it.date == date }?.let { date to (it.duration.lightMinutes + it.duration.deepMinutes + it.duration.remMinutes) }
                }.toMap(),
                dailyScores = targetDays.mapNotNull { date ->
                    sessions.find { it.date == date }?.let { date to it.sleepEfficiency }
                }.toMap(),
                dailySleepLatencyMinutes = targetDays.mapNotNull { date ->
                    sessions.find { it.date == date }?.let { date to it.duration.sleepLatencyMinutes }
                }.toMap(),
                dailyWakeCounts = targetDays.mapNotNull { date ->
                    sessions.find { it.date == date }?.let { date to it.wakeCount }
                }.toMap(),
                dailyAvgNoises = targetDays.mapNotNull { date ->
                    sessions.find { it.date == date }?.let { date to it.environment.stats.noise.avg }
                }.toMap(),
                totalWakeCount = sessions.sumOf { it.wakeCount }
            ),
            sessions
        )
    }

    private companion object {
        // 기록이 없을 때 쓰는 자리표시자 시각.
        // 예전에는 이 값들을 AlarmViewModel 과 마찬가지로 '쓰기가 한 번도 없는'
        // _trackingState 의 기본값에서 읽어 왔다. 실제 측정값처럼 보이지만 언제나
        // 23:00~07:00 고정이었다. 고정값이라는 사실이 드러나도록 상수로 옮겼다.
        const val DEFAULT_BED_HOUR = 23
        const val DEFAULT_BED_MINUTE = 0
        const val DEFAULT_WAKE_HOUR = 7
        const val DEFAULT_WAKE_MINUTE = 0

        /** 최근 평균 계산 구간(일). 7일이면 주중/주말이 한 번씩 들어가 치우침이 덜하다. */
        const val RECENT_AVERAGE_DAYS = 7

        /**
         * 측정 종료 직후의 세션 조회 재시도 예산(20 × 500ms = 10초).
         *
         * 저장은 fire-and-forget 서비스에서 ML 추론 → DB insert → 서버 전송 순으로 일어나므로
         * 리포트 진입 시점에 아직 커밋 전일 수 있다. 예전 1초(5 × 200ms)로는 밤새 분량의
         * 추론을 따라잡지 못해 거의 항상 데모 리포트로 떨어졌다.
         */
        const val SESSION_LOOKUP_RETRIES = 20
        const val SESSION_LOOKUP_RETRY_DELAY_MS = 500L
    }
}
