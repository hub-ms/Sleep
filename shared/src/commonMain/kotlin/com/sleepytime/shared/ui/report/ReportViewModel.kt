package com.sleepytime.shared.ui.report

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.enum_.ReportTab
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.util.SleepSessionUtil.toReportData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
            selectedTab = ReportTab.WEEKLY,
            date = initialDate,
            isPreview = true,
            sessionDates = emptySet(),
            reportData = DemoReportFactory.createPreviewData(0L, initialDate)
        )
    )
    val state = _state.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingContract.State())
    val trackingState = _trackingState.asStateFlow()

    private val _authState = MutableStateFlow(AuthContract.State())
    val authState = _authState.asStateFlow()

    private val currentUser: User?
        get() = _authState.value.user

    private val _effect = MutableSharedFlow<ReportContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<ReportContract.Intent>(Channel.BUFFERED)

    private val _sleepStageHistory = MutableStateFlow<List<SleepStage>>(emptyList())
    val sleepStageHistory = _sleepStageHistory.asStateFlow()

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
            handleSelectTab(_state.value.selectedTab)
        }
    }

    fun sendIntent(intent: ReportContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private fun processIntent(intent: ReportContract.Intent) {
        when (intent) {
            is ReportContract.Intent.SelectReportMode -> handleSelectTab(intent.tab)
            is ReportContract.Intent.SleepEnvironmentClicked -> {
                screenModelScope.launch {
                    _effect.emit(ReportContract.Effect.NavigateToSleepEnvironment)
                }
            }
            is ReportContract.Intent.SelectDate -> {
                val newDate = intent.date
                _state.update {
                    it.copy(date = newDate)
                }
                val epochMs = newDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                when (_state.value.selectedTab) {
                    ReportTab.DAILY -> loadReportByDate(epochMs)
                    ReportTab.WEEKLY -> loadReportByWeek(epochMs)
                    ReportTab.MONTHLY -> loadReportByMonth(epochMs)
                }
            }
            is ReportContract.Intent.LoadFinishedSession -> loadFinishedSession(intent.sessionId)
            is ReportContract.Intent.PrevMonthClicked -> {
                val currentStoredDate = _state.value.date

                // 💡 사용자가 선택한 탭에 따라 감산 단위를 다르게 적용합니다.
                val prevDate = when (_state.value.selectedTab) {
                    ReportTab.DAILY -> currentStoredDate.minus(1, DateTimeUnit.DAY)
                    ReportTab.WEEKLY -> currentStoredDate.minus(7, DateTimeUnit.DAY) // 주간 탭일 때는 1주일 전으로
                    ReportTab.MONTHLY -> currentStoredDate.minus(1, DateTimeUnit.MONTH) // 월간 탭일 때는 1달 전으로
                }

                val newEpochMs = prevDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                _state.update {
                    it.copy(date = prevDate)
                }

                when(_state.value.selectedTab) {
                    ReportTab.DAILY -> loadReportByDate(newEpochMs)
                    ReportTab.WEEKLY -> loadReportByWeek(newEpochMs)
                    ReportTab.MONTHLY -> loadReportByMonth(newEpochMs)
                }
            }
            is ReportContract.Intent.NextMonthClicked -> {
                val currentStoredDate = _state.value.date

                // 💡 사용자가 선택한 탭에 따라 가산 단위를 다르게 적용합니다.
                val nextDate = when (_state.value.selectedTab) {
                    ReportTab.DAILY -> currentStoredDate.plus(1, DateTimeUnit.DAY)
                    ReportTab.WEEKLY -> currentStoredDate.plus(7, DateTimeUnit.DAY) // 주간 탭일 때는 1주일 후로
                    ReportTab.MONTHLY -> currentStoredDate.plus(1, DateTimeUnit.MONTH) // 월간 탭일 때는 1달 후로
                }

                val newEpochMs = nextDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                _state.update {
                    it.copy(date = nextDate)
                }

                when(_state.value.selectedTab) {
                    ReportTab.DAILY -> loadReportByDate(newEpochMs)
                    ReportTab.WEEKLY -> loadReportByWeek(newEpochMs)
                    ReportTab.MONTHLY -> loadReportByMonth(newEpochMs)
                }
            }
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

    private fun handleSelectTab(tab: ReportTab) {
        val epochMs = _state.value.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        _state.update {
            it.copy(
                selectedTab = tab
            )
        }
        when (tab) {
            ReportTab.DAILY -> loadReportByDate(epochMs)
            ReportTab.WEEKLY -> loadReportByWeek(epochMs)
            ReportTab.MONTHLY -> loadReportByMonth(epochMs)
        }
    }




    private fun loadReportByDate(dateMilliseconds: Long) {
        screenModelScope.launch {
            val session = sleepSessionRepository.getSessionByDate(dateMilliseconds)
            val targetDate = Instant.fromEpochMilliseconds(dateMilliseconds).toLocalDateTime(TimeZone.currentSystemDefault()).date
            val sessionDates = getActiveSessionDatesInMonth(targetDate)

            _state.update {
                if (session == null) {
                    it.copy(
                        reportData = DemoReportFactory.createPreviewData(
                            currentUser?.userId ?: 0L, targetDate
                        ),
                        isPreview = true,
                        sessionDates = sessionDates
                    )
                } else {
                    it.copy(
                        reportData = session.toReportData(targetDate).copy(
                            dailyScores = mapOf(targetDate to session.sleepEfficiency),
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
    private fun loadReportByWeek(anchorEpochms: Long) {
        screenModelScope.launch {
            val anchor = Instant.fromEpochMilliseconds(anchorEpochms)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val monday = anchor.minus((anchor.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)
            val sessionDates = getActiveSessionDatesInMonth(anchor)

            val (reportData, sessions) = loadPeriodReportData(monday, 7)


            val finalReportData = if (sessions.isEmpty()) {
                buildPreviewPeriodData(monday, 7, currentUser?.userId ?: 0L)
            } else reportData

            _state.update {
                it.copy(
                    reportData = finalReportData,
                    isPreview = sessions.isEmpty(),
                    sessionDates = sessionDates
                )
            }
        }
    }
    private fun loadReportByMonth(anchorEpochms: Long) {
        screenModelScope.launch {
            val anchor = Instant.fromEpochMilliseconds(anchorEpochms).toLocalDateTime(TimeZone.currentSystemDefault()).date
            val firstDay = LocalDate(anchor.year, anchor.monthNumber, 1)
            val totalDays = firstDay.plus(1, DateTimeUnit.MONTH)
                .minus(1, DateTimeUnit.DAY).dayOfMonth
            val sessionDates = getActiveSessionDatesInMonth(anchor)

            val (reportData, sessions) = loadPeriodReportData(firstDay, totalDays)

            val finalReportData = if (sessions.isEmpty()) {
                buildPreviewPeriodData(firstDay, totalDays, currentUser?.userId ?: 0L)
            } else reportData

            _state.update {
                it.copy(
                    reportData = finalReportData,
                    isPreview = sessions.isEmpty(),
                    sessionDates = sessionDates
                )
            }
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
                    avgTemperature = 0f,
                    avgHumidity = 0f,
                    isHeartRateAnomaly = false,
                    isNoiseDanger = false,
                    isTempExtreme = false,
                    isHumidityExtreme = false,
                    dailyLatencyMinutes = emptyMap(),
                    dailyBedTimes = emptyMap(),
                    dailyWakeTimes = emptyMap(),
                    totalWakeCount = 0,
                    dailyScores = emptyMap(),
                    dailyAvgHeartRates = emptyMap(),
                    dailyAvgNoises = emptyMap(),
                    dailyAvgTemps = emptyMap(),
                    dailyAvgHumidities = emptyMap(),
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
                avgTemperature = sessions.map { it.environment.stats.temperature.avg }.average().toFloat(),
                avgHumidity = sessions.map { it.environment.stats.humidity.avg }.average().toFloat(),
                isHeartRateAnomaly = sessions.any { it.environment.flags.isHeartRateAnomaly },
                isNoiseDanger = sessions.any { it.environment.flags.isNoiseDanger },
                isTempExtreme = sessions.any { it.environment.flags.isTempExtreme },
                isHumidityExtreme = sessions.any { it.environment.flags.isHumidityExtreme },
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
                dailyAvgTemps = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let { date to it.environment.stats.temperature.avg }
                }.toMap(),
                dailyAvgHumidities = targetDays.mapNotNull { date ->
                    sessionMap[date]?.let { date to it.environment.stats.humidity.avg }
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
        totalDays: Int,
        userId: Long
    ): ReportContract.ReportData {
        val dailyDataList = (0 until totalDays).map { i ->
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
        val avgWakeCount = dailyDataList.map { it.second.wakeCount }.average().toInt()

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
    fun updateTrackingState(state: TrackingContract.State) {
        _trackingState.value = state
    }
}
