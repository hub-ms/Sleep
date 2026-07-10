package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.sleep.SleepSessionCreateRequest
import com.sleepytime.app.dto_new.sleep.SleepSessionUpdateRequest
import com.sleepytime.app.repository_new.SleepSessionJpaRepository
import com.sleepytime.app.repository_new.SleepStageJpaRepository
import com.sleepytime.shared.data.remote.dto.response.MetricStatsResponse
import com.sleepytime.shared.data.remote.dto.response.MonthlySleepStatsResponse
import com.sleepytime.shared.data.remote.dto.response.PeriodSummaryResponse
import com.sleepytime.shared.data.remote.dto.response.SleepSessionResponse
import com.sleepytime.shared.data.remote.dto.response.WeeklySleepStatsResponse
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.util.SleepReportCalculator
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@Service
class SleepService(
    private val sleepSessionJpaRepository: SleepSessionJpaRepository,
    private val sleepStageJpaRepository: SleepStageJpaRepository,
) {

    @Transactional
    fun create(request: SleepSessionCreateRequest): SleepSessionResponse {
        val analysisList = request.analysisList.sortedBy { it.timestamp }
        if (analysisList.isEmpty()) throw IllegalArgumentException("No analysis data found")

        val metrics = SleepReportCalculator.calculateSessionMetrics(analysisList, request.startTime)

        val efficiency = calculateEfficiency(metrics, request.environmentFeatures)

        val stageTimeline = generateStageTimeLine(request.sessionId, analysisList)
        val stagesDistribution = calculateStagesDistribution(metrics)

        // 4. 환경 변수 통계 추출
        val latestFeature = request.environmentFeatures.lastOrNull()
        val now = System.currentTimeMillis()

        // 5. 모바일 레포지토리와 동일한 완벽한 계층 구조 데이터 조립
        val sessionEntity = SleepSession(
            sessionId = request.sessionId,
            date = request.startTime,
            sleepMetrics = metrics,
            stageTimeline = stageTimeline,
            stagesDistribution = stagesDistribution,
            sleepEfficiency = efficiency,
            environment = SleepSession.Environment(
                history = request.environmentFeatures.map { it.snapshot.toDomain() },
                stats = latestFeature?.stats?.toDomain() ?: EnvironmentFeature.Statistics(),
                flags = latestFeature?.flag?.toDomain() ?: EnvironmentFeature.Flag(isHeartRateAnomaly = false, isNoiseDanger = false)
            ),
            csvData = SleepSession.CsvData(sensorCsv = "", environmentCsv = ""),
            duration = SleepSession.Duration(
                awakeMinutes = metrics.awakeMinutes,
                lightMinutes = metrics.lightMinutes,
                deepMinutes = metrics.deepMinutes,
                remMinutes = metrics.remMinutes,
                sleepLatencyMinutes = metrics.sleepLatencyMinutes
            ),
            wakeCount = metrics.wakeCount,
            timestamp = SleepSession.Timestamp(createdAt = now, updatedAt = now)
        )

        val savedEntity = sleepSessionJpaRepository.save(sessionEntity)
        return toSessionResponse(savedEntity)
    }

    @Transactional
    fun start(userId: Long): SleepSessionResponse {
        val newSession = SleepSession(
            sessionId = generateSessionId(userId),
            date = System.currentTimeMillis(), // 시작 시점을 타임스탬프로 기록
            sleepMetrics = metrics,
            stageTimeline = stageTimeline,
            stagesDistribution = stagesDistribution,
            sleepEfficiency = efficiency,
            environment = SleepSession.Environment(
                history = request.environmentFeatures.map { it.snapshot.toDomain() },
                stats = latestFeature?.stats?.toDomain() ?: EnvironmentFeature.Statistics(),
                flags = latestFeature?.flag?.toDomain() ?: EnvironmentFeature.Flag(isHeartRateAnomaly = false, isNoiseDanger = false)
            ),
            csvData = SleepSession.CsvData(sensorCsv = "", environmentCsv = ""),
            duration = SleepSession.Duration(
                awakeMinutes = metrics.awakeMinutes,
                lightMinutes = metrics.lightMinutes,
                deepMinutes = metrics.deepMinutes,
                remMinutes = metrics.remMinutes,
                sleepLatencyMinutes = metrics.sleepLatencyMinutes
            ),
            wakeCount = metrics.wakeCount,
            timestamp = SleepSession.Timestamp(createdAt = now, updatedAt = now)
        )
        val startedEntity = sleepSessionJpaRepository.save(newSession)
        return toSessionResponse(startedEntity)
    }

    @Transactional
    fun end(sessionId: Long): SleepSessionResponse {
        val session = sleepSessionJpaRepository.findById(sessionId)
            .orElseThrow { NoSuchElementException("Session not found: $sessionId") }

        // 현재 시점 기준으로 총 소요 시간(Minutes) 계산 및 각 수면 스테이지 데이터 최종 정산 로직 수행
        val currentTimestamp = System.currentTimeMillis()

        // 예시: 실시간으로 쌓인 하위 SleepStage(심박수, 데시벨) 집계 연산
        val stages = sleepStageJpaRepository.findBySleepSessionIdIn(listOf(sessionId))

        // 수면 단계 분석 및 통계 최종 마감 업데이트 실행
        session.endSessionAndCalculateStats(currentTimestamp, stages)

        val endedEntity = sleepSessionJpaRepository.save(session)
        return toSessionResponse(endedEntity)
    }

    @Transactional(readOnly = true)
    fun getById(sessionId: Long): SleepSessionResponse {
        val session = sleepSessionJpaRepository.findById(sessionId)
            .orElseThrow { NoSuchElementException("Session not found: $sessionId") }
        return toSessionResponse(session)
    }

    @Transactional(readOnly = true)
    fun getByUserId(userId: Long, pageable: Pageable): Page<SleepSessionResponse> {
        val sessionsPage = sleepSessionJpaRepository.findByUserId(userId, pageable)
        return sessionsPage.map { toSessionResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getDaily(userId: Long, date: LocalDate): List<SleepSessionResponse> {
        val startTimestamp = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endTimestamp = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1

        val sessions = sleepSessionJpaRepository.findByUserIdAndRange(userId, startTimestamp, endTimestamp)
        return sessions.map { toSessionResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getWeekly(userId: Long, date: LocalDate): WeeklySleepStatsResponse {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        val startTimestamp = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endTimestamp = weekEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1

        // 1. 이번 주 데이터 조회
        val currentSessions = sleepSessionJpaRepository.findByUserIdAndDateBetween(userId, startTimestamp, endTimestamp)
        val sessionResponses = currentSessions.map { toSessionResponse(it) }

        // 2. 지난 주 데이터 조회 (정확한 유동 범위 지정)
        val lastWeekStartTimestamp = weekStart.minusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val lastWeekEndTimestamp = startTimestamp - 1
        val lastSessions = sleepSessionJpaRepository.findByUserIdAndDateBetween(userId, lastWeekStartTimestamp, lastWeekEndTimestamp)

        // 3. 실측 요약본 연산
        val summary = calculatePeriodSummary(sessionResponses, lastSessions)

        return WeeklySleepStatsResponse(
            weekStart = weekStart.toString(),
            weekEnd = weekEnd.toString(),
            summary = summary,
            sessions = sessionResponses
        )
    }

    @Transactional(readOnly = true)
    fun getMonthly(userId: Long, year: Int, month: Int): MonthlySleepStatsResponse {
        val targetMonth = YearMonth.of(year, month)
        val monthStart = targetMonth.atDay(1)
        val monthEnd = targetMonth.atEndOfMonth()

        val startTimestamp = monthStart.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endTimestamp = monthEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1

        // 1. 이번 달 데이터 조회
        val currentSessions = sleepSessionJpaRepository.findByUserIdAndRange(userId, startTimestamp, endTimestamp)
        val sessionResponses = currentSessions.map { toSessionResponse(it) }

        // 2. 지난 달 데이터 조회
        val lastMonth = targetMonth.minusMonths(1)
        val lastMonthStartTimestamp = lastMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val lastMonthEndTimestamp = startTimestamp - 1
        val lastSessions = sleepSessionJpaRepository.findByUserIdAndRange(userId, lastMonthStartTimestamp, lastMonthEndTimestamp)

        // 3. 실측 요약본 연산
        val summary = calculatePeriodSummary(sessionResponses, lastSessions)

        return MonthlySleepStatsResponse(
            yearMonth = "%d-%02d".format(year, month),
            summary = summary,
            sessions = sessionResponses
        )
    }

    @Transactional
    fun update(sessionId: Long, request: SleepSessionUpdateRequest): SleepSessionResponse {
        val session = sleepSessionJpaRepository.findById(sessionId)
            .orElseThrow { NoSuchElementException("Session not found: $sessionId") }

        session.updateSessionDetails(
            notes = request.notes
        )

        return toSessionResponse(session)
    }

    @Transactional
    fun delete(sessionId: Long) {
        sleepStageJpaRepository.deleteBySleepSessionId(sessionId)
        sleepSessionJpaRepository.deleteById(sessionId)
    }
    private fun calculateEfficiency(metrics: SleepMetrics, features: List<EnvironmentFeature>): Int =
        metrics.toEfficiencyScore(
            isHeartRateAnomaly = features.any { it.flag.isHeartRateAnomaly },
            isNoiseDanger = features.any { it.flag.isNoiseDanger },
        )

    private fun calculateStagesDistribution(metrics: SleepMetrics): Map<SleepStageType, Float> {
        val totalMins = metrics.awakeMinutes + metrics.lightMinutes + metrics.deepMinutes + metrics.remMinutes
        if (totalMins <= 0) return emptyMap()
        return mapOf(
            SleepStageType.AWAKE to (metrics.awakeMinutes / totalMins).toFloat(),
            SleepStageType.LIGHT to (metrics.lightMinutes / totalMins).toFloat(),
            SleepStageType.DEEP to (metrics.deepMinutes / totalMins).toFloat(),
            SleepStageType.REM to (metrics.remMinutes / totalMins).toFloat(),
        )
    }

    private fun generateStageTimeLine(sessionId: String, analysisList: List<SleepAnalysis>): List<SleepStage> = buildList {
        if (analysisList.isEmpty()) return@buildList

        var currentType: SleepStageType? = null
        var currentStart: LocalDateTime? = null
        var currentDurationMs = 0L

        val defaultWindowMs = 30_000L
        val maxAllowedGapMs = 5 * 60 * 1000L

        fun flush() {
            val type = currentType ?: return
            val start = currentStart ?: return
            add(
                SleepStage(
                    sessionId = sessionId, // 클라이언트가 준 세션 ID 전달받아 매핑
                    type = type,
                    startTime = start,
                    duration = currentDurationMs.milliseconds
                )
            )
        }

        analysisList.forEachIndexed { index, analysis ->
            val nextType = when (analysis.predictionStageType) {
                PredictionStageType.AWAKE -> SleepStageType.AWAKE
                PredictionStageType.N1, PredictionStageType.N2 -> SleepStageType.LIGHT
                PredictionStageType.N3 -> SleepStageType.DEEP
                PredictionStageType.REM -> SleepStageType.REM
            }

            val windowDuration = when {
                analysis.windowDurationMs > 0L -> analysis.windowDurationMs
                index < analysisList.lastIndex -> {
                    val gap = analysisList[index + 1].timestamp - analysis.timestamp
                    if (gap in 1L..maxAllowedGapMs) gap else defaultWindowMs
                }
                else -> defaultWindowMs
            }

            when (currentType) {
                null -> {
                    currentType = nextType
                    currentStart = analysis.timestamp.toLocalDateTime()
                    currentDurationMs = windowDuration
                }
                nextType -> {
                    currentDurationMs += windowDuration
                }
                else -> {
                    flush()
                    currentType = nextType
                    currentStart = analysis.timestamp.toLocalDateTime()
                    currentDurationMs = windowDuration
                }
            }
        }
        flush()
    }

    private fun calculatePeriodSummary(
        current: List<SleepSessionResponse>,
        pastEntities: List<SleepSession> // Object -> 실제 엔티티 명세타입으로 변경 선언
    ): PeriodSummaryResponse {
        val totalCount = current.size
        if (totalCount == 0) {
            return PeriodSummaryResponse(0.0, 0.0, 0.0, 0, 0.0, 0.0)
        }

        // 1. 이번 타겟 기간 수면 지표 합산 알고리즘 구현
        val totalMinutes = current.sumOf { it.lightMinutes + it.deepMinutes + it.remMinutes }
        val avgMinutes = totalMinutes / totalCount
        val avgEfficiency = current.map { it.sleepEfficiency }.average()

        // 2. 과거 비교 기간 수면 지표 추출 및 계산식 매핑
        val pastCount = pastEntities.size
        val (pastAvgMinutes, pastAvgEfficiency) = if (pastCount > 0) {
            val pastTotalMinutes = pastEntities.sumOf { it.sleepMetrics.lightMinutes + it.sleepMetrics.deepMinutes + it.sleepMetrics.remMinutes }
            val pastAvgMin = pastTotalMinutes / pastCount
            val pastAvgEff = pastEntities.map { it.sleepEfficiency }.average()
            Pair(pastAvgMin, pastAvgEff)
        } else {
            Pair(0.0, 0.0)
        }

        return PeriodSummaryResponse(
            averageSleepMinutes = avgMinutes,
            averageSleepEfficiency = avgEfficiency,
            totalSleepMinutes = totalMinutes,
            totalSessionCount = totalCount,
            // 델타 차액 연산 적용 (이번 기간 평균 - 지난 기간 평균)
            sleepMinutesDelta = if (pastCount > 0) avgMinutes - pastAvgMinutes else 0.0,
            sleepEfficiencyDelta = if (pastCount > 0) avgEfficiency - pastAvgEfficiency else 0.0
        )
    }

    private fun toSessionResponse(entity: SleepSession): SleepSessionResponse {
        // Null 가능성이 있는 가변 통계치 필드들을 객체 조건에 맞게 세팅 조립
        val heartRateStats = if (
            entity.environment.stats.heartRate.avg != null &&
            entity.environment.stats.heartRate.max != null &&
            entity.environment.stats.heartRate.min != null
            ) {
            MetricStatsResponse(
                entity.environment.stats.heartRate.avg!!,
                entity.environment.stats.heartRate.max!!,
                entity.environment.stats.heartRate.min!!
            )
        } else null

        val noiseStats = if (
            entity.environment.stats.noise.avg != null &&
            entity.environment.stats.noise.max != null &&
            entity.environment.stats.noise.min != null
            ) {
            MetricStatsResponse(
                entity.environment.stats.noise.avg!!,
                entity.environment.stats.noise.max!!,
                entity.environment.stats.noise.min!!
            )
        } else null

        return SleepSessionResponse(
            sessionId = entity.sessionId,
            date = entity.date,
            awakeMinutes = entity.sleepMetrics.awakeMinutes,
            lightMinutes = entity.sleepMetrics.lightMinutes,
            deepMinutes = entity.sleepMetrics.deepMinutes,
            remMinutes = entity.sleepMetrics.remMinutes,
            sleepLatencyMinutes = entity.sleepMetrics.sleepLatencyMinutes,
            sleepEfficiency = entity.sleepEfficiency,
            wakeCount = entity.wakeCount,
            heartRateStats = heartRateStats,
            noiseStats = noiseStats,
            createdAt = entity.timestamp.createdAt ?: System.currentTimeMillis(),
            updatedAt = entity.timestamp.updatedAt ?: System.currentTimeMillis()
        )
    }
}