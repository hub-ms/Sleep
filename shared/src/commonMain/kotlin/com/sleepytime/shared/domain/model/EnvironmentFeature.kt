package com.sleepytime.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class EnvironmentFeature(
    val timestamp: Long,
    val snapshot: Snapshot,
    val stats: Statistics,
    val flag: Flag
) {
    @Serializable
    data class Snapshot(
        val heartRate: Float,
        val noise: Float,
        val temperature: Float,
        val humidity: Float
    )

    // 2. 가공된 통계 데이터 (기존 Stats 객체들 모음)
    @Serializable
    data class Statistics(
        val heartRate: Stats,
        val noise: Stats,
        val temperature: Stats,
        val humidity: Stats
    )

    // 3. 임계치 및 이상치 통과 여부 상태 플래그
    @Serializable
    data class Flag(
        val isHeartRateAnomaly: Boolean,
        val isNoiseDanger: Boolean,
        val isTempExtreme: Boolean,
        val isHumidityExtreme: Boolean
    )
}