

package com.soundsleeper.app.data.local.repository

import com.soundsleeper.app.data.local.dao.SleepSessionDao
import com.soundsleeper.app.data.remote.api.SleepApi
import com.soundsleeper.app.data.remote.mapper.toRequest
import com.soundsleeper.app.data.sleep.PredictionHistoryManager
import com.soundsleeper.app.data.sleep.SleepSessionFactory
import com.soundsleeper.app.data.sleep.SleepStageTimelineGenerator
import com.soundsleeper.app.data.sleep.SleepStatisticsCalculator
import com.soundsleeper.app.util.SleepAnalyzer
import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepAnalysis
import com.soundsleeper.app.domain.model.SleepSession
import com.soundsleeper.app.domain.repository.SleepSessionRepository
import com.soundsleeper.app.domain.model.Stats
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Instant

/**
 * 수면 측정/분석/저장의 실제 구현체. [AndroidTrackingManager]가 매 30초 윈도우마다
 * [analyzeSleepData]를 호출하고, 측정이 끝나면 [analyzeSleepSession]을 호출해 최종 리포트를 만들고 저장한다.
 */
class SleepSessionRepositoryImpl(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepApi: SleepApi,
    private val predictionHistoryManager: PredictionHistoryManager,
    private val sleepStatisticsCalculator: SleepStatisticsCalculator,
    private val sleepStageTimelineGenerator: SleepStageTimelineGenerator,
    private val sleepSessionFactory: SleepSessionFactory,
    private val sleepAnalyzer: SleepAnalyzer,
) : SleepSessionRepository {

    private var latestEnvironmentContext: EnvironmentFeature? = null

    @Volatile
    private var isSessionAnalyzing = false

    /**
     * 측정 종료 시 호출되는 최종 분석 함수. 그동안 쌓인 모든 실시간 예측([predictionHistoryManager])을
     * 모아 수면 단계 타임라인([sleepStageTimelineGenerator])과 단계별 분포([sleepStatisticsCalculator])를
     * 계산하고, 하나의 [SleepSession] 리포트로 조립([sleepSessionFactory])해 DB에 저장한다.
     * 성공/실패와 무관하게 끝나면 예측 히스토리와 분석기 컨텍스트를 반드시 초기화한다.
     */
    override suspend fun analyzeSleepSession(
        timestamps: List<Long>,
        environmentFeatures: List<EnvironmentFeature>,
        sessionId: String
    ): Result<SleepSession> = withContext(Dispatchers.Default) {

        runCatching {
            isSessionAnalyzing = true
            val analysisList = predictionHistoryManager.getAll()
            Napier.d("analyze: predictions=${analysisList.size}, timestamps=${timestamps.size}, " + "manager=${Clock.System.hashCode()}")
            if (analysisList.isEmpty()) throw Exception("No analysis data found")

            val trackingStartTime = timestamps.firstOrNull() ?: analysisList.first().timestamp
            val sessionDate = Instant.fromEpochMilliseconds(trackingStartTime)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            val stageTimeline = sleepStageTimelineGenerator.generate(analysisList, sessionId)

            // 🐛 버그 수정(리포트가 전부 0으로 보이던 문제): 예전에는 여기서 모든 필드가 0인
            // 임시 Duration을 만들어 calculateStagesDistribution에 넘겼습니다. 그 함수는
            // `total <= 0`이면 빈 맵을 반환하므로 단계 분포가 **항상** 비어 있었고, 아래
            // sleepSessionFactory도 duration/efficiency/wakeCount를 0으로 하드코딩하고 있어서
            // 측정이 정상적으로 끝나도 리포트의 모든 수치가 0으로 보였습니다. 구간별 실제
            // 길이는 stageTimeline에 이미 들어 있으므로 거기서 계산해 그대로 흘려보냅니다.
            val duration = sleepStatisticsCalculator.calculateDuration(stageTimeline)
            val stagesDistribution = sleepStatisticsCalculator.calculateStagesDistribution(duration)
            val sleepEfficiency = sleepStatisticsCalculator.calculateSleepEfficiency(duration)
            val wakeCount = sleepStatisticsCalculator.countWakeEpisodes(stageTimeline)

            val sessionNoiseStats = Stats.from(environmentFeatures.map { it.snapshot.noise })
            val sessionNoiseDanger = environmentFeatures.any { it.flag.isNoiseDanger }
            val now = Clock.System.now().toEpochMilliseconds()
            val session = sleepSessionFactory.create(
                sessionId = sessionId,
                sessionDate = sessionDate,
                analysisList = analysisList,
                stageTimeline = stageTimeline,
                stagesDistribution = stagesDistribution,
                duration = duration,
                sleepEfficiency = sleepEfficiency,
                wakeCount = wakeCount,
                environmentFeatures = environmentFeatures,
                sessionNoiseStats = sessionNoiseStats,
                sessionNoiseDanger = sessionNoiseDanger,
                now = now
            )
            sleepSessionDao.insertSession(session)
            Napier.d("insert sessionId=${session.sessionId}")
            syncSession(session)
            session
        }.also {
            predictionHistoryManager.clear()
            sleepAnalyzer.resetContext()
            isSessionAnalyzing = false
        }
    }
    /** 이미 만들어진 세션을 로컬 DB와 서버에 직접 저장한다(analyzeSleepSession을 거치지 않는 경로). */
    override suspend fun insertSession(
        session: SleepSession
    ) {
        sleepSessionDao.insertSession(session)
        Napier.d("insert sessionId=${session.sessionId}")
        sleepApi.saveSession(
            session.toRequest()
        )
    }

    /** 세션을 서버에 백업 동기화한다. 실패해도 로컬 저장은 이미 끝났으므로 로그만 남기고 삼킨다. */
    private suspend fun syncSession(
        session: SleepSession
    ) {
        runCatching {
            sleepApi.saveSession(
                session.toRequest()
            )
        }.onFailure {
            Napier.e(
                "수면 세션 서버 동기화 실패",
                it
            )
        }
    }

    /** 특정 날짜의 세션을 가져온다(리포트 화면에서 날짜 선택 시 사용). */
    override suspend fun getSessionByDate(date: LocalDate): SleepSession? =
        sleepSessionDao.getSessionByDate(date)

    /** 날짜 범위 내의 세션들을 가져온다(주간 리포트/캘린더 등에서 사용). */
    override suspend fun getSessionByDateRange(
        fromEpochMs: LocalDate,
        toEpochMs: LocalDate
    ): List<SleepSession> =
        sleepSessionDao.getSessionsByDateRange(fromEpochMs, toEpochMs)

    /** 세션을 삭제한다(리포트 화면의 삭제 기능, 또는 측정 취소 시). */
    override suspend fun deleteSession(sessionId: String) =
        sleepSessionDao.deleteSession(sessionId)

    /** 측정 시작 전 모델이 준비됐는지 확인한다. 준비 안 됐으면 측정을 시작하지 않도록 실패를 반환한다. */
    override suspend fun initializeModel(): Result<Unit> {
        return if (sleepAnalyzer.isReady()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("모델 초기화 실패"))
        }
    }

    /**
     * [AndroidTrackingManager.handleWindow]가 30초마다 호출하는 실시간 분석 입구.
     * 실제 추론은 [sleepAnalyzer.analyzeWindow]에 위임하고, 성공한 예측을
     * [predictionHistoryManager]에 쌓아 두어 측정 종료 시 [analyzeSleepSession]이 쓸 수 있게 한다.
     */
    override suspend fun analyzeSleepData(
        sensorData: List<FloatArray>,
        environmentFeature: EnvironmentFeature?,
        rawAudioEpochSamples: FloatArray?,
    ): Result<SleepAnalysis> = withContext(Dispatchers.Default) {
        val environmentFeature = environmentFeature ?: latestEnvironmentContext

        // 🐛 버그 수정(time_feature 채널이 항상 0이던 문제): 예전에는 세션 시작 시각을
        // `predictionHistory`라는 이 클래스의 로컬 리스트에서 읽었는데, 그 리스트에는 **아무도
        // 값을 넣지 않습니다**(예측은 전부 predictionHistoryManager로 들어갑니다). 그래서
        // firstOrNull()이 항상 null이 되어 sessionStartTime이 매 윈도우마다 "지금"으로
        // 계산됐고, 결과적으로 경과시간이 늘 0 → SleepAnalyzer의
        // `timeFeature = min(경과 / 8시간, 1.0)`이 **모든 epoch에서 0.0**이었습니다.
        // 학습 쪽 time_feature는 0~1로 변하는 값이라(ml/script/preprocess.py), 7채널 중 하나가
        // 학습/서빙에서 완전히 다른 분포로 들어가는 상태였습니다 — AccelChannels의 심박 채널
        // 제거와 같은 종류의 학습/서빙 불일치입니다. 실제 저장소인 predictionHistoryManager에서
        // 첫 예측의 타임스탬프(=세션 시작)를 읽습니다. 첫 윈도우에서는 비어 있으므로 "지금"이
        // 그대로 세션 시작이 되어 경과 0이 맞습니다.
        val history = predictionHistoryManager.getAll()
        val sessionStartTime = history.firstOrNull()?.timestamp ?: Clock.System.now().toEpochMilliseconds()
        sleepAnalyzer.analyzeWindow(sensorData, environmentFeature, sessionStartTime, rawAudioEpochSamples)
        .onSuccess { analysis ->
            Napier.d("analyzeWindow success, isSessionAnalyzing=$isSessionAnalyzing")
            if (!isSessionAnalyzing) {
                predictionHistoryManager.add(analysis)
                Napier.d("predictionHistory size=${history.size + 1}")
            }
        }
        .onFailure { Napier.e("analyzeWindow 실패", it) }
    }

    /** 분류기 리소스를 해제한다(앱 종료 등). */
    override fun closeModel() {
        sleepAnalyzer.close()
    }

    /** 모델이 추론 가능한 상태인지 반환한다. */
    override fun isReady(): Boolean = sleepAnalyzer.isReady()

    /** AndroidTrackingManager가 매 버킷 계산한 환경(소음) 정보를 최신값으로 갱신해 둔다. */
    override suspend fun updateEnvironmentContext(feature: EnvironmentFeature): Result<Unit> {
        latestEnvironmentContext = feature
        return Result.success(Unit)
    }

    /** 해당 연/월에 실제로 세션이 존재하는 날짜 목록을 가져온다(리포트 캘린더 표시용). */
    override suspend fun getSessionDatesByMonth(
        year: String,
        month: String
    ): List<LocalDate> = sleepSessionDao.getSessionDatesByMonth(year, month)

    /** 가장 최근에 저장된 세션을 가져온다(세션ID 없이 리포트 화면에 처음 들어왔을 때 사용). */
    override suspend fun getLatestSession(): SleepSession? = withContext(Dispatchers.IO) { sleepSessionDao.getLatestSession() }

    /** 특정 세션 ID로 세션을 가져온다(측정을 막 끝내고 리포트로 넘어왔을 때 그 세션을 바로 열기 위함). */
    override suspend fun getSessionById(sessionId: String): SleepSession? =
        withContext(Dispatchers.IO) { sleepSessionDao.getSessionById(sessionId) }

    /** 오늘로부터 최근 N일간의 세션들을 가져온다(최근 평균 비교 카드 등에서 사용). */
    override suspend fun getRecentSessions(days: Int): List<SleepSession> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val fromDate = today.minus(days, DateTimeUnit.DAY)
        return getSessionByDateRange(fromDate, today)
    }
}
