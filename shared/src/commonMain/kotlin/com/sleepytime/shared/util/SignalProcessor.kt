package com.sleepytime.shared.util

import kotlin.math.abs
import kotlin.math.sqrt

object SignalProcessor {

    /**
     * 1주차 로드맵 5단계: 신호 리샘플링 및 타임스탬프 정렬
     * 불규칙한 샘플들을 고정된 Hz(예: 50Hz)로 보간하여 정렬합니다.
     */
    fun resample(data: List<FloatArray>, currentHz: Int, targetHz: Int): List<FloatArray> {
        if (data.isEmpty()) return emptyList()
        val ratio = currentHz.toDouble() / targetHz.toDouble()
        val targetSize = (data.size / ratio).toInt()
        
        return List(targetSize) { i ->
            val srcIdx = i * ratio
            val low = srcIdx.toInt().coerceIn(0, data.size - 1)
            val high = (low + 1).coerceIn(0, data.size - 1)
            val weight = (srcIdx - low).toFloat()
            
            FloatArray(data[0].size) { ch ->
                data[low][ch] * (1 - weight) + data[high][ch] * weight
            }
        }
    }

    /**
     * 1주차 로드맵 7단계: 움직임 에너지 기반 게이팅
     */
    fun calculateMovementEnergy(accel: FloatArray, gyro: FloatArray): Float {
        // 중력 가속도(약 9.8)를 고려한 동적 가속도 매그니튜드 계산
        val accMag = abs(sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]) - 9.81f)
        val gyroMag = sqrt(gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2])
        return accMag + gyroMag * 2f
    }
}
