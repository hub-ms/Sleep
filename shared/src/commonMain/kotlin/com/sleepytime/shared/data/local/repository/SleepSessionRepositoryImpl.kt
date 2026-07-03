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
import com.sleepytime.shared.util.IdGenerator.generateSessionId
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

class SleepSessionRepositoryImpl(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepAnalyzer: SleepAnalyzer,
    private val authRepository: AuthRepository
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
            val metrics = SleepReportCalculator.calculateSessionMetrics(
                analysisList,
                trackingStartTime
            )
            val efficiency = calculateEfficiency(metrics, environmentFeatures)
            val stageTimeline = generateStageTimeLine(analysisList)
            val stagesDistribution = calculateStagesDistribution(metrics)

            val latestFeature = environmentFeatures.lastOrNull()
            val now = Clock.System.now().toEpochMilliseconds()
            SleepSession(
                sessionId = sessionId,
                date = trackingStartTime,
                sleepMetrics = metrics,
                stageTimeline = stageTimeline,
                stagesDistribution = stagesDistribution,
                sleepEfficiency = efficiency,
                environment = SleepSession.Environment(
                    history = environmentFeatures.map { it.snapshot },
                    stats = latestFeature?.stats ?: EnvironmentFeature.Statistics(Stats(), Stats()),
                    flags = latestFeature?.flag ?: EnvironmentFeature.Flag(
                        isHeartRateAnomaly = false,
                        isNoiseDanger = false
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
                    sleepLatencyMinutes = metrics.sleepLatencyMinutes
                ),
                wakeCount = metrics.wakeCount,
                timestamp = SleepSession.Timestamp(createdAt = now, updatedAt = now),
            )
        }.also {
            historyMutex.withLock {
                predictionHistory.clear()
            }
            isSessionAnalyzing = false
        }
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

    override suspend fun insertSession(session: SleepSession) =
        sleepSessionDao.insertSession(session)
    override suspend fun getSessionById(sessionId: String): SleepSession? =
        sleepSessionDao.getSessionById(sessionId)

    override suspend fun getSessionByDate(date: Long): SleepSession? =
        sleepSessionDao.getSessionByDate(date)

    override suspend fun getSessionByDateRange(
        fromEpochMs: Long,
        toEpochMs: Long
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
    ): List<Long> = sleepSessionDao.getSessionDatesByMonth(year, month)

    override suspend fun getLatestSession(): SleepSession? = withContext(Dispatchers.IO) { sleepSessionDao.getLatestSession() }

    private suspend fun generateStageTimeLine(analysisList: List<SleepAnalysis>): List<SleepStage> =
    buildList {
        if (analysisList.isEmpty()) return@buildList

        var currentType: SleepStageType? = null
        var currentStart: LocalDateTime? = null
        var currentDurationMs = 0L

        val defaultWindowMs = 30_000L
        val maxAllowedGapMs = 5 * 60 * 1000L

        suspend fun flush() {
            val type = currentType ?: return
            val start = currentStart ?: return
            val user = authRepository.getUserContext()
            add(
                SleepStage(
                    sessionId = generateSessionId(user),
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
