package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.EnvironmentFeature
import kotlin.text.append

class CsvExporter(private val fileSaver: FileSaver) {
    fun exportSensorData(data: List<List<FloatArray>>, fileName: String, startTimestamp: Long) {
        val sb = StringBuilder()
        // 💡 심박수 제거: 원시 윈도우 채널이 8개(accel xyz + gyro xyz + 심박 + 소음)에서
        // 7개(accel xyz + gyro xyz + 소음)로 줄었습니다.
        sb.append("timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,noise\n")
        val intervalMs = 20L

        var totalSampleIdx = 0
        data.forEach { window ->
            window.forEach { sample ->
                val ts = startTimestamp + totalSampleIdx * intervalMs
                sb.append("$ts,")
                for (i in 0 until 7) {
                    sb.append("${sample.getOrElse(i) { 0f }}${if (i < 6) "," else ""}")
                }
                sb.append("\n")
                totalSampleIdx++
            }
        }
        fileSaver.saveText(fileName, sb.toString())
    }
    fun exportEnvironmentData(features: List<EnvironmentFeature>, fileName: String) {
        val sb = StringBuilder()
        sb.append("timestamp,noise_avg,noise_std,noise_max,noise_min,isNoiseDanger\n")
        features.forEach { feat ->
            sb.append(
                "${feat.snapshot}," +
                "${feat.stats.noise.avg}," +
                "${feat.stats.noise.stddev}," +
                "${feat.stats.noise.max}," +
                "${feat.stats.noise.min}," +
                "${feat.flag.isNoiseDanger}\n")
        }
        fileSaver.saveText (fileName, sb.toString())
    }
}