

package com.sleepytime.shared.data.local.repository

import com.sleepytime.shared.data.local.dao.SleepSessionDao
import com.sleepytime.shared.ui.tracking.SleepAnalyzer
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepAnalysis
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.util.SleepReportCalculator
import com.sleepytime.shared.util.SleepReportCalculator.toEfficiencyScore
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.domain.model.Stats
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.enum_.PredictionStageType
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

class SleepSessionRepositoryImpl(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepAnalyzer: SleepAnalyzer,
) : SleepSessionRepository {

    private var latestEnvironmentContext: EnvironmentFeature? = null
    private val predictionHistory = mutableListOf<SleepAnalysis>()
    private val historyMutex = Mutex()

    @Volatile
    private var isSessionAnalyzing = false

    override suspend fun analyzeSleepSession(
        timestamps: List<Long>,
        environmentFeatures: List<EnvironmentFeature>,
        sessionId: String
    ): Result<SleepSession> = withContext(Dispatchers.Default) {
        runCatching {
            isSessionAnalyzing = true
            val analysisList = historyMutex.withLock {
                predictionHistory.sortedBy { it.timestamp }
            }
            if (analysisList.isEmpty()) throw Exception("No analysis data found")

            val trackingStartTime = timestamps.firstOrNull() ?: analysisList.first().timestamp
            val sessionDate = Instant.fromEpochMilliseconds(trackingStartTime)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            val metrics = SleepReportCalculator.calculateSessionMetrics(
                analysisList,
                trackingStartTime
            )
            val efficiency = calculateEfficiency(metrics, environmentFeatures)
            val stageTimeline = generateStageTimeLine(analysisList, sessionId)
            val stagesDistribution = calculateStagesDistribution(metrics)
            Napier.d("efficiency:$efficiency, stageTimeline:$stageTimeline, stageDistribution:$stagesDistribution")

            // 세션 전체(야간 전체)를 대표하는 소음 통계를 마지막 30초 버킷이 아니라
            // 모든 EnvironmentFeature의 noise 스냅샷을 모아 재계산합니다.
            val sessionNoiseStats = Stats.from(environmentFeatures.map { it.snapshot.noise })
            val sessionNoiseDanger = environmentFeatures.any { it.flag.isNoiseDanger }
            val now = Clock.System.now().toEpochMilliseconds()
            SleepSession(
                sessionId = sessionId,
                date = sessionDate,
                wakeTime = analysisList.last().timestamp.toLocalDateTime(),
                sleepMetrics = metrics,
                stageTimeline = stageTimeline,
                stagesDistribution = stagesDistribution,
                sleepEfficiency = efficiency,
                environment = SleepSession.Environment(
                    history = environmentFeatures.map { it.snapshot },
                    stats = EnvironmentFeature.Statistics(noise = sessionNoiseStats),
                    flags = EnvironmentFeature.Flag(
                        isNoiseDanger = sessionNoiseDanger
                    )
                ),
                csvData = SleepSession.CsvData(
                    sensorCsv = "",
                    environmentCsv = ""
                ),
                duration = SleepSession.Duration(
                    awakeMinutes = metrics.awakeMinutes,
                    lightMinutes = metrics.lightMinutes,
                    deepMinutes = metrics.deepMinutes,
                    remMinutes = metrics.remMinutes,
                    targetMinutes = 480.0,
                    sleepLatencyMinutes = metrics.sleepLatencyMinutes
                ),
                wakeCount = metrics.wakeCount,
                timestamp = SleepSession.Timestamp(createdAt = now, updatedAt = now),
            )
        }.also {
            historyMutex.withLock {
                predictionHistory.clear()
            }
            // 다음 세션이 이전 세션의 마지막 epoch들을 컨텍스트로 이어받지 않도록 초기화합니다.
            sleepAnalyzer.resetContext()
            isSessionAnalyzing = false
        }
    }
    fun calculateEfficiency(metrics: SleepMetrics, features: List<EnvironmentFeature>): Int =
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

    override suspend fun insertSession(session: SleepSession) =
        sleepSessionDao.insertSession(session)
    override suspend fun getSessionByDate(date: LocalDate): SleepSession? =
        sleepSessionDao.getSessionByDate(date)

    override suspend fun getSessionByDateRange(
        fromEpochMs: LocalDate,
        toEpochMs: LocalDate
    ): List<SleepSession> =
        sleepSessionDao.getSessionsByDateRange(fromEpochMs, toEpochMs)

    override suspend fun deleteSession(sessionId: String) =
        sleepSessionDao.deleteSession(sessionId)

    override suspend fun initializeModel(): Result<Unit> {
        return if (sleepAnalyzer.isReady()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("모델 초기화 실패"))
        }
    }

    override suspend fun analyzeSleepData(
        sensorData: List<FloatArray>,
        environmentFeature: EnvironmentFeature?
    ): Result<SleepAnalysis> = withContext(Dispatchers.Default) {
        val environmentFeature = environmentFeature ?: latestEnvironmentContext

        val sessionStartTime = predictionHistory.firstOrNull()?.timestamp ?: Clock.System.now().toEpochMilliseconds()
        sleepAnalyzer.analyzeWindow(sensorData, environmentFeature, sessionStartTime)
        .onSuccess { analysis ->
            Napier.d("analyzeWindow success, isSessionAnalyzing=$isSessionAnalyzing")
            if (!isSessionAnalyzing) {
                historyMutex.withLock {
                    predictionHistory.add(analysis)
                    Napier.d("predictionHistory size=${predictionHistory.size}")
                }
            }
        }
        .onFailure { Napier.e("analyzeWindow 실패", it) }
    }

    override fun closeModel() {
        sleepAnalyzer.close()
    }

    override fun isReady(): Boolean = sleepAnalyzer.isReady()

    override suspend fun updateEnvironmentContext(feature: EnvironmentFeature): Result<Unit> {
        latestEnvironmentContext = feature
        return Result.success(Unit)
    }

    override suspend fun getSessionDatesByMonth(
        year: String,
        month: String
    ): List<LocalDate> = sleepSessionDao.getSessionDatesByMonth(year, month)

    override suspend fun getLatestSession(): SleepSession? = withContext(Dispatchers.IO) { sleepSessionDao.getLatestSession() }
    override suspend fun getRecentSessions(days: Int): List<SleepSession> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val fromDate = today.minus(days, DateTimeUnit.DAY)
        return getSessionByDateRange(fromDate, today)
    }

    private fun generateStageTimeLine(
        analysisList: List<SleepAnalysis>,
        sessionId: String
    ): List<SleepStage> =
    buildList {
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
                    sessionId = sessionId, // 👈 생성된 sessionId를 그대로 사용
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
                    val currentMs = analysis.timestamp
                    val nextMs = analysisList[index + 1].timestamp
                    val gap = nextMs - currentMs

                    when {
                        gap in 1L..maxAllowedGapMs -> gap
                        else -> defaultWindowMs
                    }
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

    private fun Long.toLocalDateTime(): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
}
