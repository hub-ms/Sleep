package com.soundsleeper.app.util

import kotlin.math.abs
import kotlin.math.sqrt

object SignalProcessor {
    /**
     * 가속도·자이로 평균값으로부터 "움직임 에너지"를 계산한다.
     * 가속도는 중력(9.81 m/s²)에서 벗어난 만큼만 움직임으로 보고(정지 시 0에 가까움),
     * 자이로 크기는 회전 움직임을 더 강하게 반영하도록 2배 가중한다.
     * [SleepAnalyzer.analyzeWindow]에서 N3(깊은 수면) 오판정을 걸러내는 보정에 쓰인다.
     */
    fun calculateMovementEnergy(accel: FloatArray, gyro: FloatArray): Float {
        val accMag = abs(sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]) - 9.81f)
        val gyroMag = sqrt(gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2])
        return accMag + gyroMag * 2f
    }
}
