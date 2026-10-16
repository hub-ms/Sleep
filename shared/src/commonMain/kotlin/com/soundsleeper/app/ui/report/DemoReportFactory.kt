package com.soundsleeper.app.ui.report

import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepStage
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.enum_.SleepStageType
import com.soundsleeper.app.util.IdGenerator.generateSessionId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 실제 측정 세션이 없는 사용자/기간에 리포트 화면이 보여줄 그럴듯한 데모 데이터를 만드는 팩토리. */
object DemoReportFactory {
    /**
     * 하루치 데모 리포트를 만든다. (userId, targetDate)로 시드를 고정해, 같은 날짜를 다시 요청해도
     * 항상 같은 데모 데이터가 나오게 한다(화면을 오갈 때 값이 바뀌어 보이지 않도록).
     */
    fun createPreviewData(
        userId: Long,
        targetDate: LocalDate,
        targetMinutes: Double = 480.0
    ): ReportContract.ReportData {
        val seed = userId.hashCode() + targetDate.toEpochDays()
        val random = Random(seed)

        val rawMetrics = generateRawSleepMetrics(random)

        val (bedTime, wakeTime, sleepMinutes) = calculateConstrainedSleepTimes(targetDate, rawMetrics.totalInBed, rawMetrics.latency, rawMetrics.awake, random)

        val scaleRatio = if (sleepMinutes < rawMetrics.totalInBed) sleepMinutes / rawMetrics.totalInBed else 1.0
        val awakeMinutes = rawMetrics.awake * scaleRatio
        val lightMinutes = rawMetrics.light * scaleRatio
        val deepMinutes = rawMetrics.deep * scaleRatio
        val remMinutes = rawMetrics.rem * scaleRatio

        val stageTimeline = generateTimelineMatchedWithDurations(
            baseStart = bedTime,
            latencyMinutes = rawMetrics.latency,
            awakeMax = awakeMinutes,
            lightMax = lightMinutes,
            deepMax = deepMinutes,
            remMax = remMinutes,
            random = random
        )

        val (avgNoise, isNoiseDanger) = generateNoiseMetrics(random)

        val environmentHistory = generateEnvironmentHistory(
            bedTime = bedTime,
            wakeTime = wakeTime,
            avgNoise = avgNoise,
            random = random
        )


        val sleepScore = when {
            isNoiseDanger -> random.nextInt(50, 80)
            rawMetrics.wakeCount >= 5 -> random.nextInt(55, 85)
            rawMetrics.latency >= 45 -> random.nextInt(60, 90)
            else -> random.nextInt(80, 100)
        }

        return ReportContract.ReportData(
            sessionId = generateSessionId(user = User.AuthInfo.Guest),
            bedTime = bedTime,
            wakeTime = wakeTime,
            sleepScore = sleepScore,
            sleepLatencyMinutes = rawMetrics.latency,
            environmentHistory = environmentHistory,
            awakeMinutes = awakeMinutes,
            lightMinutes = lightMinutes,
            deepMinutes = deepMinutes,
            remMinutes = remMinutes,
            sleepMinutes = sleepMinutes,
            targetMinutes = targetMinutes,
            stageTimeline = stageTimeline,
            wakeCount = rawMetrics.wakeCount,
            avgNoise = avgNoise,
            isNoiseDanger = isNoiseDanger,
            dailyBedTimes = mapOf(targetDate to bedTime),
            dailyWakeTimes = mapOf(targetDate to wakeTime),
            dailySleepMinutes = mapOf(targetDate to sleepMinutes),
            dailyScores = mapOf(targetDate to sleepScore),
            dailySleepLatencyMinutes = mapOf(targetDate to rawMetrics.latency),
            dailyWakeCounts = mapOf(targetDate to rawMetrics.wakeCount),
            dailyAvgNoises = mapOf(targetDate to avgNoise),
            totalWakeCount = rawMetrics.wakeCount,
        )
    }
    private data class RawSleepMetrics(
        val awake: Double,
        val light: Double,
        val deep: Double,
        val rem: Double,
        val latency: Double,
        val wakeCount: Int
    ) {
        val totalInBed: Double get() = awake + light + deep + rem
    }
    private fun generateRawSleepMetrics(random: Random): RawSleepMetrics {
        val baseAwake = random.nextInt(18, 25).toDouble()
        val baseLight = random.nextInt(180, 241).toDouble()
        val baseDeep = random.nextInt(72, 97).toDouble()
        val baseRem = random.nextInt(90, 121).toDouble()
        val baseWakeCount = random.nextInt(0, 3)

        val latency = when (random.nextInt(100)) {
            in 0..74   -> random.nextInt(6, 21).toDouble()
            in 75..79  -> random.nextInt(21, 41).toDouble()
            in 80..89  -> random.nextInt(45, 91).toDouble()
            else -> {
                if (random.nextBoolean())
                    random.nextInt(0, 6).toDouble()
                else
                    random.nextInt(41, 46).toDouble()
            }
        }

        return when (random.nextInt(100)) {
            in 0..69 -> RawSleepMetrics(
                awake = baseAwake,
                light = baseLight,
                deep = baseDeep,
                rem = baseRem,
                latency = latency,
                wakeCount = baseWakeCount
            )
            in 70..89 -> RawSleepMetrics(
                awake = baseAwake * 1.6,
                light = baseLight * 1.05,
                deep = baseDeep * 0.75,
                rem = baseRem * 0.85,
                latency = latency,
                wakeCount = baseWakeCount + random.nextInt(1, 3)
            )
            else -> RawSleepMetrics(
                awake = baseAwake * random.nextDouble(5.0, 8.0),
                light = baseLight * 0.8,
                deep = baseDeep * 0.2,
                rem = baseRem * 0.4,
                latency = latency,
                wakeCount = baseWakeCount + random.nextInt(4, 8)
            )
        }
    }
    private fun calculateConstrainedSleepTimes(
        targetDate: LocalDate,
        totalInBed: Double,
        latency: Double,
        awake: Double,
        random: Random
    ): Triple<LocalDateTime, LocalDateTime, Double> {
        val tz = TimeZone.currentSystemDefault()
        val baseBedTime = LocalDateTime(targetDate.year, targetDate.month, targetDate.day, 23, 0)
            .toInstant(tz)

        val bedTime = when (random.nextInt(100)) {
            in 0..69 -> baseBedTime.plus(random.nextInt(0, 121).minutes)
            in 70..89 -> baseBedTime.plus(random.nextInt(120, 211).minutes)
            else -> {
                if (random.nextBoolean())
                    baseBedTime.minus(random.nextInt(60, 121).minutes)
                else
                    baseBedTime.plus(random.nextInt(210, 301).minutes)
            }
        }.toLocalDateTime(tz)

        val wakeTime = bedTime.toInstant(tz)
            .plus(totalInBed.minutes).toLocalDateTime(tz)

        val sleepMinutes = (totalInBed - latency - awake).coerceAtLeast(0.0)

        return Triple(bedTime, wakeTime, sleepMinutes)
    }

    private fun generateNoiseMetrics(random: Random): Pair<Float, Boolean> {
        return when (random.nextInt(100)) {
            in 0..69 -> Pair(random.nextInt(20, 36).toFloat(), false)
            in 70..89 -> Pair(random.nextInt(36, 46).toFloat(), false)
            else -> Pair(random.nextInt(46, 66).toFloat(), true)
        }
    }


    /** 여러 날짜에 대해 [createPreviewData]를 각각 생성한 뒤 일별 맵들을 하나로 합친다(주간/월간 데모 데이터용). */
    fun createRangePreviewData(userId: Long, dates: List<LocalDate>): ReportContract.ReportData {
        require(dates.isNotEmpty()) { "dates는 비어있을 수 없습니다." }

        val combinedDailyScores = mutableMapOf<LocalDate, Int>()
        val combinedDailyBedTimes = mutableMapOf<LocalDate, LocalDateTime>()
        val combinedDailyWakeTimes = mutableMapOf<LocalDate, LocalDateTime>()
        val combinedDailySleepMinutes = mutableMapOf<LocalDate, Double>()
        val combinedDailyLatency = mutableMapOf<LocalDate, Double>()
        val combinedDailyWakeCounts = mutableMapOf<LocalDate, Int>()
        val combinedDailyNoises = mutableMapOf<LocalDate, Float>()

        for (date in dates) {
            val singleDayData = createPreviewData(userId = userId, targetDate = date)
            combinedDailyScores[date] = singleDayData.sleepScore
            combinedDailyBedTimes[date] = singleDayData.bedTime
            combinedDailyWakeTimes[date] = singleDayData.wakeTime
            combinedDailySleepMinutes[date] = singleDayData.sleepMinutes
            combinedDailyLatency[date] = singleDayData.sleepLatencyMinutes
            combinedDailyWakeCounts[date] = singleDayData.wakeCount
            combinedDailyNoises[date] = singleDayData.avgNoise
        }

        val baseReport = createPreviewData(userId = userId, targetDate = dates.last())

        return baseReport.copy(
            dailyBedTimes = combinedDailyBedTimes,
            dailyWakeTimes = combinedDailyWakeTimes,
            dailySleepMinutes = combinedDailySleepMinutes,
            dailyScores = combinedDailyScores,
            dailySleepLatencyMinutes = combinedDailyLatency,
            dailyWakeCounts = combinedDailyWakeCounts,
            dailyAvgNoises = combinedDailyNoises,
        )
    }
    private fun generateTimelineMatchedWithDurations(
        baseStart: LocalDateTime,
        latencyMinutes: Double,
        awakeMax: Double,
        lightMax: Double,
        deepMax: Double,
        remMax: Double,
        random: Random
    ): List<SleepStage> {
        val timeline = mutableListOf<SleepStage>()
        val tz = TimeZone.currentSystemDefault()
        var currentInstant = baseStart.toInstant(tz)

        var awakeEpochs = (awakeMax * 2).toInt()
        var lightEpochs = (lightMax * 2).toInt()
        var deepEpochs = (deepMax * 2).toInt()
        var remEpochs = (remMax * 2).toInt()

        val latencyEpochs = (latencyMinutes * 2).toInt()
        repeat(latencyEpochs) {
            timeline.add(
                SleepStage(
                    sessionId = generateSessionId(user = User.AuthInfo.Guest),
                    type = SleepStageType.AWAKE,
                    startTime = currentInstant.toLocalDateTime(tz),
                    duration = 30.seconds
                )
            )
            currentInstant = currentInstant.plus(30.seconds)
        }

        if (latencyMinutes > 0) {
            timeline.add(
                SleepStage(
                    sessionId = generateSessionId(user = User.AuthInfo.Guest),
                    type = SleepStageType.AWAKE,
                    startTime = currentInstant.toLocalDateTime(tz),
                    duration = latencyMinutes.minutes
                )
            )
            currentInstant = currentInstant.plus(latencyMinutes.minutes)
        }
        val stageOrder = listOf(
            SleepStageType.AWAKE,
            SleepStageType.LIGHT,
            SleepStageType.DEEP,
            SleepStageType.LIGHT,
            SleepStageType.REM
        )

        // 2. 남은 수면 단계들을 30초(1 Epoch) 단위 블록으로 번갈아가며 배분
        while (awakeEpochs > 0 || lightEpochs > 0 || deepEpochs > 0 || remEpochs > 0) {
            val stageSequence = stageOrder.shuffled(random) // 자연스러운 순서 조율
            for (type in stageSequence) {
                val availableEpochs = when (type) {
                    SleepStageType.AWAKE -> awakeEpochs
                    SleepStageType.LIGHT -> lightEpochs
                    SleepStageType.DEEP -> deepEpochs
                    SleepStageType.REM -> remEpochs
                }

                if (availableEpochs <= 0) continue

                // 연속된 블록 크기 설정 (30초~10분 사이 = 1~20개 에포크)
                val chunkCount = minOf(availableEpochs, random.nextInt(2, 21))

                repeat(chunkCount) {
                    timeline.add(
                        SleepStage(
                            sessionId = generateSessionId(user = User.AuthInfo.Guest),
                            type = type,
                            startTime = currentInstant.toLocalDateTime(tz),
                            duration = 30.seconds
                        )
                    )
                    currentInstant = currentInstant.plus(30.seconds)
                }

                when (type) {
                    SleepStageType.AWAKE -> awakeEpochs -= chunkCount
                    SleepStageType.LIGHT -> lightEpochs -= chunkCount
                    SleepStageType.DEEP -> deepEpochs -= chunkCount
                    SleepStageType.REM -> remEpochs -= chunkCount
                }
            }
        }
        return timeline
    }
    private fun generateEnvironmentHistory(
        bedTime: LocalDateTime,
        wakeTime: LocalDateTime,
        avgNoise: Float,
        random: Random
    ): List<EnvironmentFeature.Snapshot> { // 👈 본인 프로젝트의 EnvironmentData 타입 지정
        val history = mutableListOf<EnvironmentFeature.Snapshot>()
        val tz = TimeZone.currentSystemDefault()

        var currentInstant = bedTime.toInstant(tz)
        val endInstant = wakeTime.toInstant(tz)

        while (currentInstant <= endInstant) {
            // 미세한 변동폭 추가 (노이즈 생성)
            val noiseVariation = random.nextDouble(-2.0, 2.0).toFloat()
            val currentNoise = (avgNoise + noiseVariation).coerceAtLeast(0.0f)

            history.add(
                EnvironmentFeature.Snapshot(
                    noise = currentNoise
                )
            )
            currentInstant = currentInstant.plus(30.seconds)
        }
        return history
    }
}