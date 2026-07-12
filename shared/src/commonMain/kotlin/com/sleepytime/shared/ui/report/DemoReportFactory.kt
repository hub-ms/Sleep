package com.sleepytime.shared.ui.report

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.util.SleepReportCalculator
import com.sleepytime.shared.util.SleepReportCalculator.calculateBoundedScore
import com.sleepytime.shared.util.SleepReportCalculator.calculateContinuityScore
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.util.IdGenerator.generateSessionId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

object DemoReportFactory {
    fun createPreviewData(userId: Long, targetDate: LocalDate): ReportContract.ReportData {
        val seed = userId.hashCode() + targetDate.toEpochDays()
        val random = Random(seed)


        val environmentHistory = emptyList<EnvironmentFeature.Snapshot>()

        val baseAwake = random.nextInt(18, 25).toDouble()
        val baseLight = random.nextInt(180, 241).toDouble()
        val baseDeep  = random.nextInt(72, 97).toDouble()
        val baseRem   = random.nextInt(90, 121).toDouble()
        val baseWake  = random.nextInt(0, 3)

        val conditionRoll = random.nextInt(100)

        val awakeMinutes: Double
        val lightMinutes: Double
        val deepMinutes: Double
        val remMinutes: Double
        val wakeCount: Int

        // 💡 상태(Condition) 분포 밸런스 조정
        when {
            // 🥇 [아주 좋음 - 15%] roll: 0 ~ 14
            conditionRoll < 15 -> {
                awakeMinutes = (baseAwake * 0.6).coerceAtLeast(5.0)
                lightMinutes = baseLight * 0.95
                deepMinutes = baseDeep * 1.15
                remMinutes = baseRem * 1.1
                wakeCount = (baseWake - 1).coerceAtLeast(0)
            }
            // 🥈 [좋음 - 40%] roll: 15 ~ 54
            conditionRoll < 55 -> {
                awakeMinutes = baseAwake
                lightMinutes = baseLight
                deepMinutes = baseDeep
                remMinutes = baseRem
                wakeCount = baseWake
            }
            // 🥉 [보통 - 25%] roll: 55 ~ 79
            conditionRoll < 80 -> {
                awakeMinutes = baseAwake * 1.6
                lightMinutes = baseLight * 1.05
                deepMinutes = baseDeep * 0.75
                remMinutes = baseRem * 0.85
                wakeCount = baseWake + random.nextInt(1, 3)
            }
            // 🚨 [개선 필요 / 빨간색 점수 확보 - 20%] roll: 80 ~ 99
            else -> {
                // 💡 깨어 있는 시간을 대폭 늘려(120분~180분) 수면 효율 점수를 60점 미만 레드존으로 하락 유도
                awakeMinutes = baseAwake * Random.nextDouble(5.0, 8.0)
                lightMinutes = baseLight * 0.8
                // 깊은 수면과 렘수면을 가차없이 축소
                deepMinutes = baseDeep * 0.2
                remMinutes = baseRem * 0.4
                // 수면 중 중간에 깬 횟수를 다량 누적 (점수 감점 요인)
                wakeCount = baseWake + random.nextInt(4, 8)
            }
        }

        val totalMinutes = lightMinutes + deepMinutes + remMinutes
        val timeInBed = awakeMinutes + totalMinutes

        val bedTimeRoll = random.nextInt(100)
        val bedTimeInstant = when {
            bedTimeRoll < 75 -> {
                LocalDateTime(targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth, 23, 0)
                    .toInstant(TimeZone.currentSystemDefault())
                    .plus(random.nextInt(0, 121).minutes)
            }
            bedTimeRoll < 95 -> {
                LocalDateTime(targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth, 1, 0)
                    .toInstant(TimeZone.currentSystemDefault())
                    .plus(random.nextInt(0, 121).minutes)
            }
            else -> {
                LocalDateTime(targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth, 21, 30)
                    .toInstant(TimeZone.currentSystemDefault())
                    .minus(random.nextInt(0, 91).minutes)
            }
        }
        val bedTime = bedTimeInstant.toLocalDateTime(TimeZone.currentSystemDefault())

        val wakeTimeInstant = bedTimeInstant.plus(timeInBed.minutes)
        val wakeTime = wakeTimeInstant.toLocalDateTime(TimeZone.currentSystemDefault())

        val stageTimeline = generateTimelineMatchedWithDurations(
            baseStart = bedTime,
            awakeMax = awakeMinutes,
            lightMax = lightMinutes,
            deepMax = deepMinutes,
            remMax = remMinutes,
        )

        // 💡 입면 지연 시간(Latency)도 최악의 상태(else)일 때는 강제로 늘려 콤보 감점 적용
        val latencyRoll = random.nextInt(100)
        val sleepLatencyMinutes: Double = when {
            conditionRoll >= 80 -> random.nextInt(45, 90).toDouble() // 🚨 수면 질이 나쁜 날은 잠드는 데도 오래 걸림
            latencyRoll < 75 -> random.nextInt(5, 21).toDouble()
            latencyRoll < 90 -> random.nextInt(21, 41).toDouble()
            else -> random.nextInt(0, 6).toDouble()
                .let { if (random.nextBoolean()) it else random.nextInt(41, 61).toDouble() }
        }

        val advices = mutableListOf<String>()

        val heartRateRoll = random.nextInt(100)
        val avgHeartRate: Float
        val isHeartRateAnomaly: Boolean
        if (heartRateRoll < 70) {
            avgHeartRate = random.nextInt(41, 101).toFloat()
            isHeartRateAnomaly = false
        } else if (heartRateRoll < 85) {
            avgHeartRate = random.nextInt(1,41).toFloat()
            isHeartRateAnomaly = true
            advices.add("수면 중 평균 심박수가 평소보다 낮게 측정되었습니다. 컨디션을 점검해보세요.")
        } else {
            avgHeartRate = random.nextInt(101, 131).toFloat()
            isHeartRateAnomaly = true
            advices.add("수면 중 심박수가 다소 높습니다. 음주나 야식이 원인일 수 있습니다.")
        }

        val noiseRoll = random.nextInt(100)
        val avgNoise: Float
        val isNoiseDanger: Boolean
        if (noiseRoll < 70) {
            avgNoise = random.nextInt(1, 41).toFloat()
            isNoiseDanger = false
        } else {
            avgNoise = random.nextInt(41, 61).toFloat()
            isNoiseDanger = true
            advices.add("침실 소음이 높습니다. 코골이나 이갈이, 혹은 외부 소음이 있었는지 확인해보세요.")
        }

        val tempRoll = random.nextInt(100)
        val avgTemperature: Float
        val isTempExtreme: Boolean
        if (tempRoll < 70) {
            avgTemperature = (random.nextInt(180, 241) / 10f)
            isTempExtreme = false
        } else if (tempRoll < 85) {
            avgTemperature = (random.nextInt(120, 171) / 10f)
            isTempExtreme = true
            advices.add("침실 온도가 너무 낮아 수면을 방해할 수 있습니다. 난방이나 이불을 신경 써주세요.")
        } else {
            avgTemperature = (random.nextInt(250, 301) / 10f)
            isTempExtreme = true
            advices.add("침실 온도가 수면 적정 온도보다 높습니다. 시원한 환경을 조성해보세요.")
        }

        val humidityRoll = random.nextInt(100)
        val avgHumidity: Float
        val isHumidityExtreme: Boolean
        if (humidityRoll < 70) {
            avgHumidity = random.nextInt(41, 61).toFloat()
            isHumidityExtreme = false
        } else if (humidityRoll < 85) {
            avgHumidity = random.nextInt(1, 41).toFloat()
            isHumidityExtreme = true
            advices.add("침실이 다소 건조합니다. 가습기를 활용해 습도를 45~55%로 맞춰보세요.")
        } else {
            avgHumidity = random.nextInt(61, 101).toFloat()
            isHumidityExtreme = true
            advices.add("침실 습도가 높아 끩끩할 수 있습니다. 환기나 제습이 필요합니다.")
        }

        if (advices.isEmpty()) {
            advices.add("오늘 밤은 수면에 아주 이상적인 환경이 유지되었습니다.")
        }

        val deepPct = if (totalMinutes > 0) (deepMinutes / totalMinutes) * 100.0 else 0.0
        val remPct  = if (totalMinutes > 0) (remMinutes  / totalMinutes) * 100.0 else 0.0


        val wakeCountScore  = (100.0 - ((wakeCount - 2).coerceAtLeast(0) * 8.0)).coerceIn(0.0, 100.0)
        val continuityScore = calculateContinuityScore(awakeMinutes, totalMinutes, wakeCount)
        val deepScore = calculateBoundedScore(deepPct, 10.0, 25.0)
        val remScore = calculateBoundedScore(remPct,  15.0, 25.0)
        val latencyScore = when {
            sleepLatencyMinutes <= 10 -> 100.0
            sleepLatencyMinutes <= 20 -> 80.0
            sleepLatencyMinutes <= 30 -> 60.0
            sleepLatencyMinutes <= 45 -> 30.0
            else -> 0.0
        }

        val sleepMetrics = SleepMetrics(
            wakeCountScore = wakeCountScore,
            continuityScore = continuityScore,
            deepScore = deepScore,
            remScore = remScore,
            latencyScore = latencyScore,
            awakeMinutes = awakeMinutes,
            lightMinutes = lightMinutes,
            deepMinutes = deepMinutes,
            remMinutes = remMinutes,
            sleepLatencyMinutes = sleepLatencyMinutes,
            wakeCount = wakeCount
        )

        val sleepScore = with(SleepReportCalculator) { sleepMetrics.toEfficiencyScore(
            isHeartRateAnomaly = isHeartRateAnomaly,
            isNoiseDanger = isNoiseDanger,
        ) }


        return ReportContract.ReportData(
            bedTime = bedTime,
            wakeTime = wakeTime,
            sleepMetrics = sleepMetrics,
            environmentHistory = environmentHistory,

            awakeMinutes = awakeMinutes,
            lightMinutes = lightMinutes,
            deepMinutes = deepMinutes,
            remMinutes = remMinutes,
            totalMinutes = totalMinutes,
            stageTimeline = stageTimeline,
            sleepLatencyMinutes = sleepLatencyMinutes,
            wakeCount = wakeCount,
            sleepScore = sleepScore,
            avgHeartRate = avgHeartRate,
            avgNoise = avgNoise,
            isHeartRateAnomaly = isHeartRateAnomaly,
            isNoiseDanger = isNoiseDanger,
            dailyLatencyMinutes = mapOf(targetDate to sleepLatencyMinutes),
            dailyBedTimes = mapOf(targetDate to bedTime),
            dailyWakeTimes = mapOf(targetDate to wakeTime),
            totalWakeCount = wakeCount,
            dailyScores = mapOf(targetDate to sleepScore),
            dailyAvgHeartRates = mapOf(targetDate to avgHeartRate),
            dailyAvgNoises = mapOf(targetDate to avgNoise),

        )
    }
    fun createWeeklyPreviewData(userId: Long = 0L, endDate: LocalDate): ReportContract.ReportData {
        val dates = (0..6).map { offset -> endDate.minus(offset, DateTimeUnit.DAY) }.reversed()
        return createRangePreviewData(userId = userId, dates = dates)
    }
    fun createRangePreviewData(userId: Long, dates: List<LocalDate>): ReportContract.ReportData {
        require(dates.isNotEmpty()) { "dates는 비어있을 수 없습니다." }

        val combinedDailyScores = mutableMapOf<LocalDate, Int>()
        val combinedDailyBedTimes = mutableMapOf<LocalDate, LocalDateTime>()
        val combinedDailyWakeTimes = mutableMapOf<LocalDate, LocalDateTime>()
        val combinedDailyLatency = mutableMapOf<LocalDate, Double>()
        val combinedDailyHeartRates = mutableMapOf<LocalDate, Float>()
        val combinedDailyNoises = mutableMapOf<LocalDate, Float>()

        for (date in dates) {
            val singleDayData = createPreviewData(userId = userId, targetDate = date)
            combinedDailyScores[date] = singleDayData.sleepScore
            combinedDailyBedTimes[date] = singleDayData.bedTime
            combinedDailyWakeTimes[date] = singleDayData.wakeTime
            combinedDailyLatency[date] = singleDayData.sleepLatencyMinutes
            combinedDailyHeartRates[date] = singleDayData.avgHeartRate
            combinedDailyNoises[date] = singleDayData.avgNoise
        }

        // 대표 리포트는 range의 마지막 날짜(=오늘에 가장 가까운 날) 기준으로 삼음
        val baseReport = createPreviewData(userId = userId, targetDate = dates.last())

        return baseReport.copy(
            dailyScores = combinedDailyScores,
            dailyBedTimes = combinedDailyBedTimes,
            dailyWakeTimes = combinedDailyWakeTimes,
            dailyLatencyMinutes = combinedDailyLatency,
            dailyAvgHeartRates = combinedDailyHeartRates,
            dailyAvgNoises = combinedDailyNoises,
        )
    }
    private fun generateTimelineMatchedWithDurations(
        baseStart: LocalDateTime,
        awakeMax: Double,
        lightMax: Double,
        deepMax: Double,
        remMax: Double,
    ): List<SleepStage> {
        val timeline = mutableListOf<SleepStage>()
        var currentInstant = baseStart.toInstant(TimeZone.currentSystemDefault())

        var totalRemainingAwake = awakeMax
        var totalRemainingLight = lightMax
        var totalRemainingDeep = deepMax
        var totalRemainingRem = remMax

        val stageOrder = listOf(
            SleepStageType.AWAKE,
            SleepStageType.LIGHT,
            SleepStageType.DEEP,
            SleepStageType.LIGHT,
            SleepStageType.REM
        )

        while (totalRemainingAwake > 0 || totalRemainingLight > 0 || totalRemainingDeep > 0 || totalRemainingRem > 0) {
            val totalLeftBudget =
                totalRemainingAwake + totalRemainingLight + totalRemainingRem + totalRemainingDeep
            val currentCycleLength = minOf(90.0, totalLeftBudget)

            val cycleAwakeLimit = (currentCycleLength * 0.05f).coerceAtLeast(1.0)
            val cycleLightLimit = (currentCycleLength * 0.50f).coerceAtLeast(1.0)
            val cycleDeepLimit = (currentCycleLength * 0.20f).coerceAtLeast(1.0)
            val cycleRemLimit = (currentCycleLength * 0.25f).coerceAtLeast(1.0)

            // 이번 사이클 안에서 소비할 각 수면 단계별 가용 예산(가득 채울 수 있는 상한선)
            var cycleRemainingAwake = minOf(totalRemainingAwake, cycleAwakeLimit)
            var cycleRemainingLight = minOf(totalRemainingLight, cycleLightLimit)
            var cycleRemainingDeep = minOf(totalRemainingDeep, cycleDeepLimit)
            var cycleRemainingRem = minOf(totalRemainingRem, cycleRemLimit)

            var orderIdx = 0

            while ((cycleRemainingAwake > 0 || cycleRemainingLight > 0 || cycleRemainingDeep > 0 || cycleRemainingRem > 0) &&
                (totalRemainingAwake > 0 || totalRemainingLight > 0 || totalRemainingDeep > 0 || totalRemainingRem > 0)
            ) {
                val currentType = stageOrder[orderIdx % stageOrder.size]
                orderIdx++

                val stageCycleBudget = when (currentType) {
                    SleepStageType.AWAKE -> cycleRemainingAwake
                    SleepStageType.LIGHT -> cycleRemainingLight
                    SleepStageType.DEEP -> cycleRemainingDeep
                    SleepStageType.REM -> cycleRemainingRem
                }
                if (stageCycleBudget <= 0) continue

                val allocatedMinutes = if (stageCycleBudget <= 8) {
                    stageCycleBudget
                } else {
                    Random.nextDouble(4.0, minOf(stageCycleBudget, 20.0)).coerceAtLeast(1.0)
                }

                when (currentType) {
                    SleepStageType.AWAKE -> {
                        cycleRemainingAwake -= allocatedMinutes
                        totalRemainingAwake -= allocatedMinutes
                    }

                    SleepStageType.LIGHT -> {
                        cycleRemainingLight -= allocatedMinutes
                        totalRemainingLight -= allocatedMinutes
                    }

                    SleepStageType.DEEP -> {
                        cycleRemainingDeep -= allocatedMinutes
                        totalRemainingDeep -= allocatedMinutes
                    }

                    SleepStageType.REM -> {
                        cycleRemainingRem -= allocatedMinutes
                        totalRemainingRem -= allocatedMinutes
                    }
                }

                val duration = allocatedMinutes.minutes
                timeline.add(
                    SleepStage(
                        sessionId = generateSessionId(
                            user = User.AuthInfo.Guest
                        ),
                        type = currentType,
                        startTime = currentInstant.toLocalDateTime(TimeZone.currentSystemDefault()),
                        duration = duration
                    )
                )
                currentInstant = currentInstant.plus(duration)
            }
            if (currentCycleLength == 90.0 && (cycleRemainingAwake > 0 || cycleRemainingLight > 0 || cycleRemainingDeep > 0 || cycleRemainingRem > 0)) {
                totalRemainingAwake -= cycleRemainingAwake
                totalRemainingLight -= cycleRemainingLight
                totalRemainingDeep -= cycleRemainingDeep
                totalRemainingRem -= cycleRemainingRem
            }
        }
        return timeline
    }

}