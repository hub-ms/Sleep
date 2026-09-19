package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.sleep.SleepSessionCreateRequest
import com.sleepytime.app.repository_new.SleepSessionJpaRepository
import com.sleepytime.app.repository_new.SleepStageJpaRepository
import com.sleepytime.app.entity_new.SleepSessionEntity
import com.sleepytime.shared.data.remote.dto.response.MetricStatsResponse
import com.sleepytime.shared.data.remote.dto.response.PeriodSummaryResponse
import com.sleepytime.shared.data.remote.dto.response.SleepSessionResponse
import com.sleepytime.shared.data.remote.dto.response.WeeklySleepStatsResponse
import com.sleepytime.shared.domain.model.*
import com.sleepytime.shared.enum_.*
import com.sleepytime.shared.util.SleepReportCalculator
import com.sleepytime.shared.util.SleepReportCalculator.toEfficiencyScore
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.LocalDateTime as KLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.time.Duration.Companion.milliseconds

@Service
class SleepService(
    private val sleepSessionJpaRepository: SleepSessionJpaRepository,
    private val sleepStageJpaRepository: SleepStageJpaRepository,
) {

    @Transactional
    fun start(request: SleepSessionCreateRequest): SleepSessionResponse {
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
        val sessionDomain = SleepSession(
            sessionId = request.sessionId,
            date = KInstant.fromEpochMilliseconds(request.startTime).toLocalDateTime(TimeZone.currentSystemDefault()).date,
            sleepMetrics = metrics,
            wakeTime = request.endTime.toKLocalDateTime(),
            stageTimeline = stageTimeline,
            stagesDistribution = stagesDistribution,
            sleepEfficiency = efficiency,
            environment = SleepSession.Environment(
                history = request.environmentFeatures.map { it.snapshot },
                stats = latestFeature?.stats ?: EnvironmentFeature.Statistics(Stats()),
                flags = latestFeature?.flag ?: EnvironmentFeature.Flag(isNoiseDanger = false)
            ),
            csvData = SleepSession.CsvData(sensorCsv = "", environmentCsv = ""),
            duration = SleepSession.Duration(
                awakeMinutes = metrics.awakeMinutes,
                lightMinutes = metrics.lightMinutes,
                deepMinutes = metrics.deepMinutes,
                remMinutes = metrics.remMinutes,
                targetMinutes = 480.0,
                sleepLatencyMinutes = metrics.sleepLatencyMinutes
            ),
            wakeCount = metrics.wakeCount,
            timestamp = SleepSession.Timestamp(createdAt = now, updatedAt = now)
        )

        // JPA 엔티티로 변환하여 저장
        val jpaEntity = SleepSessionEntity(
            userId = request.userId,
            startAt = java.time.Instant.ofEpochMilli(request.startTime),
            endAt = java.time.Instant.ofEpochMilli(request.endTime),
            durationSec = ((request.endTime - request.startTime) / 1000).toInt(),
            sleepScore = efficiency
        )

        val savedEntity = sleepSessionJpaRepository.save(jpaEntity)
        // 리스폰스는 도메인 모델 기반으로 생성 (여기서는 저장된 엔티티 대신 생성한 도메인 모델 사용)
        return toSessionResponse(sessionDomain)
    }
    @Transactional
    fun end(sessionId: Long): SleepSessionResponse {
        val entity = sleepSessionJpaRepository.findById(sessionId)
            .orElseThrow { NoSuchElementException("Session not found: $sessionId") }

        // 현재 시점 기준으로 총 소요 시간(Minutes) 계산 및 각 수면 스테이지 데이터 최종 정산 로직 수행
        val currentTimestamp = System.currentTimeMillis()

        // 예시: 실시간으로 쌓인 하위 SleepStage(심박수, 데시벨) 집계 연산
        val stages = sleepStageJpaRepository.findBySleepSessionIdIn(listOf(sessionId))

        // TODO: 수면 단계 분석 및 통계 최종 마감 업데이트 실행
        // entity.endSessionAndCalculateStats(currentTimestamp, stages)

        val endedEntity = sleepSessionJpaRepository.save(entity)
        return toSessionResponse(endedEntity)
    }

    @Transactional(readOnly = true)
    fun getById(sessionId: Long): SleepSessionResponse {
        val entity = sleepSessionJpaRepository.findById(sessionId)
            .orElseThrow { NoSuchElementException("Session not found: $sessionId") }
        return toSessionResponse(entity)
    }

    @Transactional(readOnly = true)
    fun getByUserId(userId: Long, pageable: Pageable): Page<SleepSessionResponse> {
        // findByUserId 가 없으므로 findAll 사용하거나 적절한 메소드 호출
        val sessionsPage = sleepSessionJpaRepository.findAll(pageable) 
        return sessionsPage.map { toSessionResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getDaily(userId: Long, date: LocalDate): List<SleepSessionResponse> {
        val startInstant = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        val endInstant = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)

        val sessions = sleepSessionJpaRepository.findByUserIdAndRange(userId, startInstant, endInstant)
        return sessions.map { toSessionResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getWeekly(userId: Long, date: LocalDate): WeeklySleepStatsResponse {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        val startInstant = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC)
        val endInstant = weekEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)

        // 1. 이번 주 데이터 조회
        val currentSessions = sleepSessionJpaRepository.findByUserIdAndRange(userId, startInstant, endInstant)
        val sessionResponses = currentSessions.map { toSessionResponse(it) }

        // 2. 지난 주 데이터 조회
        val lastWeekStartInstant = weekStart.minusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val lastWeekEndInstant = startInstant.minusMillis(1)
        val lastSessions = sleepSessionJpaRepository.findByUserIdAndRange(userId, lastWeekStartInstant, lastWeekEndInstant)

        // 3. 실측 요약본 연산
        // calculatePeriodSummary 가 List<SleepSession> 을 받으므로 변환 필요
        val summary = calculatePeriodSummary(sessionResponses, lastSessions.map { it.toDomainStub() })

        return WeeklySleepStatsResponse(
            weekStart = weekStart.toString(),
            weekEnd = weekEnd.toString(),
            summary = summary,
            sessions = sessionResponses
        )
    }
    @Transactional
    fun delete(sessionId: Long) {
        sleepSessionJpaRepository.deleteById(sessionId)
    }
    private fun calculateEfficiency(metrics: SleepMetrics, features: List<EnvironmentFeature>): Int =
        metrics.toEfficiencyScore(
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
        var currentStart: KLocalDateTime? = null
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

        analysisList.forEachIndexed { index: Int, analysis: SleepAnalysis ->
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
                    currentStart = analysis.timestamp.toKLocalDateTime()
                    currentDurationMs = windowDuration
                }
                nextType -> {
                    currentDurationMs += windowDuration
                }
                else -> {
                    flush()
                    currentType = nextType
                    currentStart = analysis.timestamp.toKLocalDateTime()
                    currentDurationMs = windowDuration
                }
            }
        }
        flush()
    }

    private fun Long.toKLocalDateTime(): KLocalDateTime =
        KInstant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

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

    private fun toSessionResponse(entity: SleepSessionEntity): SleepSessionResponse {
        return SleepSessionResponse(
            sessionId = entity.id.toString(),
            date = entity.startAt.atZone(ZoneOffset.UTC).toLocalDate().toKxLocalDate(),
            awakeMinutes = 0.0,
            lightMinutes = 0.0,
            deepMinutes = 0.0,
            remMinutes = 0.0,
            sleepLatencyMinutes = 0.0,
            sleepEfficiency = entity.sleepScore ?: 0,
            wakeCount = 0,
            noiseStats = null,
            createdAt = entity.createdAt.toEpochMilli(),
            updatedAt = entity.updatedAt.toEpochMilli()
        )
    }

    private fun SleepSessionEntity.toDomainStub(): SleepSession {
        val now = System.currentTimeMillis()
        return SleepSession(
            sessionId = id.toString(),
            wakeTime = LocalDateTime(1970,1,1,0,0),
            date = startAt.atZone(ZoneOffset.UTC).toLocalDate().toKxLocalDate(),
            sleepMetrics = SleepMetrics(),
            stageTimeline = emptyList(),
            stagesDistribution = emptyMap(),
            sleepEfficiency = sleepScore ?: 0,
            environment = SleepSession.Environment(emptyList(), EnvironmentFeature.Statistics(Stats()), EnvironmentFeature.Flag(false)),
            csvData = SleepSession.CsvData("", ""),
            duration = SleepSession.Duration(0.0, 0.0, 0.0, 0.0, 480.0, 0.0),
            wakeCount = 0,
            timestamp = SleepSession.Timestamp(createdAt.toEpochMilli(), updatedAt.toEpochMilli())
        )
    }

    private fun java.time.LocalDate.toKxLocalDate(): kotlinx.datetime.LocalDate =
        kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)

    private fun toSessionResponse(entity: SleepSession): SleepSessionResponse {
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
            noiseStats = noiseStats,
            createdAt = entity.timestamp.createdAt ?: System.currentTimeMillis(),
            updatedAt = entity.timestamp.updatedAt ?: System.currentTimeMillis()
        )
    }
}