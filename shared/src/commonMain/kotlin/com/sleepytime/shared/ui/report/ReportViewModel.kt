
package com.sleepytime.shared.ui.report

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.util.IdGenerator.generateSessionId
import com.sleepytime.shared.util.SleepReportCalculator
import com.sleepytime.shared.util.SleepReportCalculator.calculateBoundedScore
import com.sleepytime.shared.util.SleepReportCalculator.calculateContinuityScore
import com.sleepytime.shared.util.SleepReportCalculator.calculateLatencyScore
import com.sleepytime.shared.util.SleepReportCalculator.calculateWakeCountScore
import com.sleepytime.shared.util.SleepSessionUtil.toReportData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
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
import kotlin.time.Duration.Companion.minutes

@ExperimentalTime
class ReportViewModel(
    private val sleepSessionRepository: SleepSessionRepository,
    private val authRepository: AuthRepository
) : ScreenModel {
    private val initialDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val _state = MutableStateFlow(
        ReportContract.State(
            reportData = DemoReportFactory.createPreviewData(0L, initialDate),
            weeklyChartData = DemoReportFactory.createPreviewData(0L, initialDate)
        )
    )
    val state = _state.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingContract.State())
    val trackingState = _trackingState.asStateFlow()

    private val _authState = MutableStateFlow(AuthContract.State())

    private val _effect = Channel<ReportContract.Effect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val currentUser: User?
        get() = _authState.value.user

    private val _intentChannel = Channel<ReportContract.Intent>(Channel.BUFFERED)

    init {
        screenModelScope.launch {
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

    fun sendIntent(intent: ReportContract.Intent) {
        screenModelScope.launch {
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
        screenModelScope.launch {
            val latestSession = sleepSessionRepository.getLatestSession()
            val latestDate = latestSession?.date ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
            loadData(latestDate)
        }
    }

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
                _state.update { it.copy(showDeleteDialog = false, pendingDeleteSessionId = null) }
                loadData(_state.value.date)
            }
            is ReportContract.Intent.DismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, pendingDeleteSessionId = null) }
            }
        }
    }

    private fun handleNavigation(isNext: Boolean, unit: DateTimeUnit.DateBased) {
        if (_state.value.isPreview || _state.value.isCalendarExpanded) {
            val newDate = if (isNext) _state.value.date.plus(1, unit)
            else _state.value.date.minus(1, unit)
            loadData(newDate)
        } else {
            screenModelScope.launch {
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

    private fun loadData(date: LocalDate) {
        screenModelScope.launch {
            _state.update { it.copy(date = date) }

            val (monthSessionsMap, sessionDates) = getMonthSessionsAndDates(date)

            if (monthSessionsMap.isEmpty()) {
                loadPreviewReport(date)
                return@launch
            }

            val prevDate = date.minus(1, DateTimeUnit.DAY)
            val prevSession = sleepSessionRepository.getSessionByDate(prevDate)
            val prevDayData = prevSession?.toReportData(prevDate)

            val monday = date.minus((date.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)
            val prevMonday = monday.minus(7, DateTimeUnit.DAY)

            val (currentWeekData, curSessions) = loadPeriodReportData(monday, 7)
            val (prevWeekData, prevSessions) = loadPeriodReportData(prevMonday, 7)

            val finalCurrentWeekData = if (curSessions.isEmpty()) null else currentWeekData
            val finalPrevWeekData = if (prevSessions.isEmpty()) null else prevWeekData

            val session = monthSessionsMap[date]
            val dayData = session?.toReportData(date)
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
                    dayData?.bedTime?.hour ?: trackingState.value.trackingStartTime.hour,
                    dayData?.bedTime?.minute ?: trackingState.value.trackingStartTime.minute,
                )
                val totalMin = session.duration.awakeMinutes + session.duration.lightMinutes +
                        session.duration.deepMinutes + session.duration.remMinutes
                val wakeTime = bedTime
                    .toInstant(TimeZone.currentSystemDefault())
                    .plus(totalMin.toLong(), DateTimeUnit.MINUTE)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val sleepMinutes = session.duration.lightMinutes + session.duration.deepMinutes + session.duration.remMinutes

                val deepPct = if (totalMin > 0) (session.duration.deepMinutes / totalMin) * 100.0 else 0.0
                val remPct  = if (totalMin > 0) (session.duration.remMinutes / totalMin) * 100.0 else 0.0

                val metrics = SleepMetrics(
                    wakeCountScore = calculateWakeCountScore(session.wakeCount),
                    continuityScore = calculateContinuityScore(
                        awakeMinutes = session.duration.awakeMinutes,
                        sleepMinutes = sleepMinutes,
                        latency = session.duration.sleepLatencyMinutes,
                        wakeCount = session.wakeCount
                    ),
                    deepScore = calculateBoundedScore(deepPct, 10.0, 25.0),
                    remScore = calculateBoundedScore(remPct, 15.0, 25.0),
                    latencyScore = calculateLatencyScore(session.duration.sleepLatencyMinutes),

                    awakeMinutes = session.duration.awakeMinutes,
                    lightMinutes = session.duration.lightMinutes,
                    deepMinutes = session.duration.deepMinutes,
                    remMinutes = session.duration.remMinutes,
                    sleepLatencyMinutes = session.duration.sleepLatencyMinutes,
                    wakeCount = session.wakeCount
                )

                _state.update {
                    it.copy(
                        date = date,
                        reportData = session.toReportData(date).copy(
                            dailyBedTimes = mapOf(date to bedTime),
                            dailyWakeTimes = mapOf(date to wakeTime),
                            dailySleepMinutes = mapOf(date to sleepMinutes),
                            dailyScores = monthScores + mapOf(date to session.sleepEfficiency),
                            dailySleepLatencyMinutes = mapOf(date to session.duration.sleepLatencyMinutes),
                            dailyAvgNoises = mapOf(date to session.environment.stats.noise.avg),
                            sleepMetrics = metrics
                        ),
                        weeklyChartData = finalCurrentWeekData ?: session.toReportData(date),
                        prevWeeklyChartData = finalPrevWeekData,
                        prevDayReportData = prevDayData,
                        isPreview = false,
                        sessionDates = sessionDates
                    )
                }
            }
        }
    }
    private fun loadPreviewReport(date: LocalDate) {
        val userId = currentUser?.userId ?: 0L
        val firstDayOfMonth = LocalDate(date.year, date.monthNumber, 1)

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
    private suspend fun getMonthSessionsAndDates(date: LocalDate): Pair<Map<LocalDate, SleepSession>, Set<LocalDate>> {
        val firstDay = LocalDate(date.year, date.monthNumber, 1).minus(15, DateTimeUnit.DAY)
        val lastDay = LocalDate(date.year, date.monthNumber, 1).plus(2, DateTimeUnit.MONTH)

        val sessions = sleepSessionRepository.getSessionByDateRange(firstDay, lastDay)

        val sessionsMap = sessions.associateBy { it.date }
        val sessionDates = sessionsMap.keys

        return Pair(sessionsMap, sessionDates)
    }

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
                    bedTime = trackingState.value.trackingStartTime,
                    wakeTime = trackingState.value.trackingEndTime,
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
                    sleepMetrics = SleepMetrics(
                        wakeCountScore = 0.0,
                        continuityScore = 0.0,
                        deepScore = 0.0,
                        remScore = 0.0,
                        latencyScore = 0.0,
                        awakeMinutes = 0.0,
                        lightMinutes = 0.0,
                        deepMinutes = 0.0,
                        remMinutes = 0.0,
                        sleepLatencyMinutes = 0.0,
                        wakeCount = 0
                    )
                ),
                sessions
            )
        }
        Napier.d("sessions=$sessions")
        val sessionReports = sessions.map { it to it.toReportData(it.date) }
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

                LocalDateTime(startDate.year, startDate.monthNumber, startDate.dayOfMonth, hour, minute)
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

                LocalDateTime(startDate.year, startDate.monthNumber, startDate.dayOfMonth, hour, minute)
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
}