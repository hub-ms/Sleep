package com.sleepytime.shared.ui.report

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.enum_.ChartTab
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.util.SleepSessionUtil.toReportData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.todayIn
import kotlin.collections.emptyList

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

    private val currentUser: User?
        get() = _authState.value.user

    private val _intentChannel = Channel<ReportContract.Intent>(Channel.BUFFERED)

    init {
        screenModelScope.launch {
            launch {
                _intentChannel.receiveAsFlow().collect { processIntent(it) }
            }
            val user = authRepository.getUser()
            user?.let {
                _authState.update {
                    it.copy(user = user)
                }
            }
            loadData(_state.value.date)
        }
    }

    fun sendIntent(intent: ReportContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private fun processIntent(intent: ReportContract.Intent) {
        when (intent) {
            is ReportContract.Intent.SelectChartMode -> handleChartTab(intent.chartTab)
            is ReportContract.Intent.SelectDate -> loadData(intent.date)
            is ReportContract.Intent.LoadFinishedSession -> loadFinishedSession(intent.sessionId)
            is ReportContract.Intent.PrevMonthClicked -> loadData(_state.value.date.minus(1, DateTimeUnit.MONTH))
            is ReportContract.Intent.NextMonthClicked -> loadData(_state.value.date.plus(1, DateTimeUnit.MONTH))
        }
    }

    private fun loadFinishedSession(sessionId: String) {
        screenModelScope.launch {
            val session = sleepSessionRepository.getSessionById(sessionId) ?: return@launch

            val targetDate = Instant.fromEpochMilliseconds(session.date)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date

            _state.update {
                it.copy(
                    date       = targetDate,
                    isPreview  = false,
                    reportData = session.toReportData(targetDate),
                )
            }
        }
    }
    private fun handleChartTab(tab: ChartTab) {
        _state.update { it.copy(selectedChartTab = tab) }
    }
    private fun loadData(targetDate: LocalDate) {
        val epochMs = targetDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        loadDailyReport(epochMs, targetDate)
        loadWeeklyChart(targetDate)
    }
    private fun loadDailyReport(dateMilliseconds: Long, targetDate: LocalDate) {
        screenModelScope.launch {
            val session = sleepSessionRepository.getSessionByDate(dateMilliseconds)
            val sessionDates = getActiveSessionDatesInMonth(targetDate)
            val monthSessionsMap = getMonthSessionsMap(targetDate)
            val monthScores = monthSessionsMap.mapValues { (_, s) -> s.sleepEfficiency }

            _state.update {
                if (session == null) {
                    it.copy(
                        date = targetDate,
                        reportData = DemoReportFactory.createPreviewData(
                            currentUser?.userId ?: 0L, targetDate
                        ).copy(dailyScores = monthScores),
                        isPreview = true,
                        sessionDates = sessionDates
                    )
                } else {
                    it.copy(
                        date = targetDate,
                        reportData = session.toReportData(targetDate).copy(
                            dailyScores = monthScores + mapOf(targetDate to session.sleepEfficiency),
                            dailyBedTimes = mapOf(
                                targetDate to Instant.fromEpochMilliseconds(session.date)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                            ),
                            dailyWakeTimes = mapOf(
                                targetDate to Instant.fromEpochMilliseconds(session.date)
                                    .plus(
                                        (session.duration.awakeMinutes + session.duration.lightMinutes +
                                                session.duration.deepMinutes + session.duration.remMinutes).toLong(),
                                        DateTimeUnit.MINUTE
                                    ).toLocalDateTime(TimeZone.currentSystemDefault())
                            )
                        ),
                        isPreview = false,
                        sessionDates = sessionDates
                    )
                }
            }
        }
    }
    private fun loadWeeklyChart(targetDate: LocalDate) {
        screenModelScope.launch {
            val monday = targetDate.minus((targetDate.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)

            val (reportData, sessions) = loadPeriodReportData(monday, 7)
            val finalReportData = if (sessions.isEmpty()) {
                buildPreviewPeriodData(monday, currentUser?.userId ?: 0L)
            } else reportData

            _state.update {
                it.copy(
                    weeklyChartData = finalReportData
                )
            }
        }
    }
    private suspend fun getActiveSessionDatesInMonth(targetDate: LocalDate): Set<LocalDate> {
        val firstDay = LocalDate(targetDate.year, targetDate.monthNumber, 1)
        val lastDay = firstDay.plus(1, DateTimeUnit.MONTH)
        val monthSessions = sleepSessionRepository.getSessionByDateRange(
            firstDay.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            lastDay.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )
        return monthSessions.map {
            Instant.fromEpochMilliseconds(it.date).toLocalDateTime(TimeZone.currentSystemDefault()).date
        }.toSet()
    }
    private suspend fun getMonthSessionsMap(targetDate: LocalDate): Map<LocalDate, SleepSession> {
        val firstDay = LocalDate(targetDate.year, targetDate.monthNumber, 1)
        val lastDay = firstDay.plus(1, DateTimeUnit.MONTH)
        val tz = TimeZone.currentSystemDefault()
        return sleepSessionRepository.getSessionByDateRange(
            firstDay.atStartOfDayIn(tz).toEpochMilliseconds(),
            lastDay.atStartOfDayIn(tz).toEpochMilliseconds()
        ).associateBy {
            Instant.fromEpochMilliseconds(it.date).toLocalDateTime(tz).date
        }
    }
    private suspend fun loadPeriodReportData(
        startDate: LocalDate,
        totalDays: Int
    ): Pair<ReportContract.ReportData, List<SleepSession>> {
        val fromEpochMs = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val toEpochMs = startDate.plus(totalDays, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val sessions = sleepSessionRepository.getSessionByDateRange(fromEpochMs, toEpochMs)
        val targetDays = (0 until totalDays).map { startDate.plus(it, DateTimeUnit.DAY) }

        if (sessions.isEmpty()) {
            return Pair(
                ReportContract.ReportData(
                    bedTime = trackingState.value.trackingStartTime,
                    wakeTime = trackingState.value.trackingEndTime,
                    environmentHistory = emptyList(),
                    awakeMinutes = 0.0,
                    lightMinutes = 0.0,
                    deepMinutes = 0.0,
                    remMinutes = 0.0,
                    totalMinutes = 0.0,
                    stageTimeline = emptyList(),
                    sleepLatencyMinutes = 0.0,
                    wakeCount = 0,
                    sleepScore = 0,
                    avgHeartRate = 0f,
                    avgNoise = 0f,
                    isHeartRateAnomaly = false,
                    isNoiseDanger = false,
                    dailyLatencyMinutes = emptyMap(),
                    dailyBedTimes = emptyMap(),
                    dailyWakeTimes = emptyMap(),
                    totalWakeCount = 0,
                    dailyScores = emptyMap(),
                    dailyAvgHeartRates = emptyMap(),
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

        val tz = TimeZone.currentSystemDefault()
        val sessionMap = sessions.associateBy {
            Instant.fromEpochMilliseconds(it.date).toLocalDateTime(tz).date
        }

        val avgBedTime = sessions
            .map { Instant.fromEpochMilliseconds(it.date).toLocalDateTime(tz) }
            .let { times ->
                val avgMinutes = times.map { it.hour * 60 + it.minute }.average().toInt()
                // 대표 날짜는 startDate 기준
                LocalDateTime(
                    startDate.year, startDate.monthNumber, startDate.dayOfMonth,
                    avgMinutes / 60, avgMinutes % 60
                )
            }

        val avgWakeTime = sessions
            .map { session ->
                val timeInBed = session.duration.awakeMinutes + session.duration.lightMinutes +
                        session.duration.deepMinutes + session.duration.remMinutes
                Instant.fromEpochMilliseconds(session.date)
                    .plus(timeInBed.toLong(), DateTimeUnit.MINUTE)
                    .toLocalDateTime(tz)
            }
            .let { times ->
                val avgMinutes = times.map { it.hour * 60 + it.minute }.average().toInt()
                LocalDateTime(
                    startDate.year, startDate.monthNumber, startDate.dayOfMonth,
                    avgMinutes / 60, avgMinutes % 60
                )
            }

        return Pair(
            ReportContract.ReportData(
                bedTime = avgBedTime,
                wakeTime = avgWakeTime,
                environmentHistory = sessions.flatMap { it.environment.history },
                awakeMinutes = sessions.sumOf { it.duration.awakeMinutes },
                lightMinutes = sessions.sumOf { it.duration.lightMinutes },
                deepMinutes = sessions.sumOf { it.duration.deepMinutes },
                remMinutes = sessions.sumOf { it.duration.remMinutes },
                totalMinutes = sessions.sumOf { it.duration.lightMinutes + it.duration.deepMinutes + it.duration.remMinutes },
                stageTimeline = sessions.flatMap { it.stageTimeline },
                sleepLatencyMinutes = sessions.map { it.duration.sleepLatencyMinutes }.average(),
                wakeCount = sessions.sumOf { it.wakeCount },
                sleepScore = sessions.map { it.sleepEfficiency }.average().toInt(),
                avgHeartRate = sessions.map { it.environment.stats.heartRate.avg }.average().toFloat(),
                avgNoise = sessions.map { it.environment.stats.noise.avg }.average().toFloat(),
                isHeartRateAnomaly = sessions.any { it.environment.flags.isHeartRateAnomaly },
                isNoiseDanger = sessions.any { it.environment.flags.isNoiseDanger },
                dailyLatencyMinutes = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let {
                        date to it.duration.sleepLatencyMinutes
                    }
                }.toMap(),
                dailyBedTimes = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let {
                        date to Instant.fromEpochMilliseconds(it.date).toLocalDateTime(tz)
                    }
                }.toMap(),
                dailyWakeTimes = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let { session ->
                        val totalMin = session.duration.awakeMinutes + session.duration.lightMinutes +
                                session.duration.deepMinutes + session.duration.remMinutes
                        date to Instant.fromEpochMilliseconds(session.date)
                            .plus(totalMin.toLong(), DateTimeUnit.MINUTE).toLocalDateTime(tz)
                    }
                }.toMap(),
                dailyAvgHeartRates = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let { date to it.environment.stats.heartRate.avg }
                }.toMap(),
                dailyAvgNoises = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let { date to it.environment.stats.noise.avg }
                }.toMap(),
                totalWakeCount = sessions.sumOf { it.wakeCount },
                dailyScores = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let { date to it.sleepEfficiency }
                }.toMap(),
            ),
            sessions
        )
    }
    private fun buildPreviewPeriodData(
        startDate: LocalDate,
        userId: Long
    ): ReportContract.ReportData {
        val dailyDataList = (0 until 7).map { i ->
            val date = startDate.plus(i, DateTimeUnit.DAY)
            date to DemoReportFactory.createPreviewData(userId, date)
        }
        val allLatencyMinutes = dailyDataList.mapNotNull { (date, d) -> d.dailyLatencyMinutes[date]?.let { date to it } }.toMap()
        val allBedTimes  = dailyDataList.mapNotNull { (date, d) -> d.dailyBedTimes[date]?.let { date to it } }.toMap()
        val allWakeTimes = dailyDataList.mapNotNull { (date, d) -> d.dailyWakeTimes[date]?.let { date to it } }.toMap()
        val allScores    = dailyDataList.mapNotNull { (date, d) -> d.dailyScores[date]?.let { date to it } }.toMap()

        val firstData = dailyDataList.first().second

        val avgBedTime = allBedTimes.values
            .map { it.hour * 60 + it.minute }.average().toInt()
            .let { LocalDateTime(startDate.year, startDate.monthNumber, startDate.dayOfMonth, it / 60, it % 60) }

        val avgWakeTime = allWakeTimes.values
            .map { it.hour * 60 + it.minute }.average().toInt()
            .let { LocalDateTime(startDate.year, startDate.monthNumber, startDate.dayOfMonth, it / 60, it % 60) }

        val avgLatencyMinutes = allLatencyMinutes.values.average()

        val avgAwakeMinutes = dailyDataList.map { it.second.awakeMinutes }.average()
        val avgLightMinutes = dailyDataList.map { it.second.lightMinutes }.average()
        val avgDeepMinutes = dailyDataList.map { it.second.deepMinutes }.average()
        val avgRemMinutes = dailyDataList.map { it.second.remMinutes }.average()

        val avgWakeCountScore = dailyDataList.map { it.second.sleepMetrics.wakeCountScore }.average()
        val avgContinuityScore = dailyDataList.map { it.second.sleepMetrics.continuityScore }.average()
        val avgDeepScore = dailyDataList.map { it.second.sleepMetrics.deepScore }.average()
        val avgRemScore = dailyDataList.map { it.second.sleepMetrics.remScore }.average()
        val avgLatencyScore = dailyDataList.map { it.second.sleepMetrics.latencyScore }.average()

        return firstData.copy(
            awakeMinutes = avgAwakeMinutes,
            lightMinutes = avgLightMinutes,
            deepMinutes = avgDeepMinutes,
            remMinutes = avgRemMinutes,
            totalMinutes = avgLightMinutes + avgDeepMinutes + avgRemMinutes,
            sleepLatencyMinutes = avgLatencyMinutes,
            bedTime = avgBedTime,
            wakeTime = avgWakeTime,
            sleepScore = allScores.values.average().toInt(),
            dailyLatencyMinutes = allLatencyMinutes,
            dailyBedTimes = allBedTimes,
            dailyWakeTimes = allWakeTimes,
            dailyScores = allScores,
            stageTimeline = dailyDataList.flatMap { it.second.stageTimeline },
            sleepMetrics = SleepMetrics(
                wakeCountScore = avgWakeCountScore,
                continuityScore = avgContinuityScore,
                deepScore = avgDeepScore,
                remScore = avgRemScore,
                latencyScore = avgLatencyScore,
                awakeMinutes = avgAwakeMinutes,
                lightMinutes = avgLightMinutes,
                deepMinutes = avgDeepMinutes,
                remMinutes = avgRemMinutes,
                sleepLatencyMinutes = avgLatencyMinutes,
                wakeCount = 0
            )
        )
    }
}
