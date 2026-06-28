package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.EnvironmentFeature
import kotlin.text.append

class CsvExporter(private val fileSaver: FileSaver) {
    fun exportSensorData(data: List<List<FloatArray>>, fileName: String, startTimestamp: Long) {
        val sb = StringBuilder()
        sb.append ("timestamp,accel_x,accel_y,accel_z,mic_db,light_lux\n")
        val intervalMs = 20L // 50Hz
        data.forEachIndexed { idx, sample ->
            val ts = startTimestamp + idx * intervalMs
            sb.append(
                "$ts,${sample.getOrElse(0) { 0f }},${sample.getOrElse(1) { 0f }}," +
                "${sample.getOrElse(2) { 0f }},${sample.getOrElse(3) { 0f }},${sample.getOrElse(4) { 0f }}\n"
            )
        }
        fileSaver.saveText (fileName, sb.toString())
    }
    fun exportEnvironmentData(features: List<EnvironmentFeature>, fileName: String) {
        val sb = StringBuilder()
        sb.append(
            "timestamp,heart_rate,temperature,humidity,hr_avg,hr_std,hr_min,hr_max,noise_avg,n oise_std, noise_max, temp_avg, temp_std, temp_min, temp_max, humid_avg, humid_std, humid_min, humid_max, isNoiseDanger, isTempExtreme, isHeartRateAnomaly\n")
        features.forEach { feat ->
            sb.append(
                "${feat.snapshot}," +
                "${feat.stats.heartRate.avg},${feat.stats.noise.avg},${feat.stats.temperature.avg},${feat.stats.humidity.avg}," +
                "${feat.stats.heartRate.stddev},${feat.stats.noise.stddev},${feat.stats.temperature.stddev},${feat.stats.humidity.stddev}," +
                "${feat.stats.heartRate.max},${feat.stats.noise.max},${feat.stats.temperature.max},${feat.stats.humidity.max}," +
                "${feat.stats.heartRate.min},${feat.stats.noise.min},${feat.stats.temperature.min},${feat.stats.humidity.min}," +
                "${feat.flag.isHeartRateAnomaly }, ${ feat.flag.isNoiseDanger }, ${ feat.flag.isTempExtreme }, ${ feat.flag.isHumidityExtreme }\n")
        }
        fileSaver.saveText (fileName, sb.toString())
    }
}