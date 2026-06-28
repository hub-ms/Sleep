package com.sleepytime.shared.platform

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

actual class SleepStageClassifier {
    actual companion object {
        actual val CHANNEL_MEAN = floatArrayOf(-0.0000f, 0.0000f, 9.7986f, 68.5769f, 40.0434f, 10.8971f, 23.5710f, 50.3241f)
        actual val CHANNEL_STD  = floatArrayOf(0.1668f, 0.1668f, 0.2047f, 9.1942f, 8.9610f, 6.3135f, 3.1568f, 11.4427f)

        actual const val CH_ACCEL_X     = 0
        actual const val CH_ACCEL_Y     = 1
        actual const val CH_ACCEL_Z     = 2
        actual const val CH_TILT_ANGLE  = 3
        actual const val CH_HEART_RATE  = 4
        actual const val CH_MIC         = 5
        actual const val CH_MFCC_ENERGY = 6
        actual const val CH_TIME_FEATURE = 7
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    actual fun classifySleepStage(sensorData: List<List<FloatArray>>): Int {
        if (!isInitialized || interpreter == null) throw IllegalStateException("모델 초기화 실패")

        val inputBuffer = buildInputBuffer(sensorData)

        val outputBuffer = ByteBuffer.allocateDirect(1 * 5 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter?.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val prob = FloatArray(5)
        outputBuffer.asFloatBuffer().get(prob)
        return prob.indices.maxByOrNull { prob[it] } ?: 0
    }

    actual fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }

    actual fun isReady() = isInitialized && interpreter != null

    private fun buildInputBuffer(sensorData: List<List<FloatArray>>): ByteBuffer {
        val flat = sensorData.flatten()
        check(flat.size == 1500) { "데이터 사이즈 불일치 ${flat.size} != 1500" }

        val buf = ByteBuffer.allocateDirect(1 * 1500 * 8 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        for (timeStep in flat) {
            check(timeStep.size == 8) {
                "채널 수 불일치 ${timeStep.size} != 8"
            }
            for (ch in 0 until 8) {
                val value = timeStep[ch]
                val mean = CHANNEL_MEAN[ch]
                val std = CHANNEL_STD[ch]
                buf.putFloat((value - mean) / std)
            }
        }

        buf.rewind()
        return buf
    }

    private fun loadModelFile(assetManager: AssetManager, modelName: String): ByteBuffer {
        val fd = assetManager.openFd(modelName)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun initialize(context: Context) {
        if(isInitialized) return
        try {
            val assetManager = context.assets
            val modelBuffer = loadModelFile(assetManager, "sleep_model.tflite")
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                setNumThreads(4)
            })
            interpreter?.allocateTensors()
            isInitialized = true
        } catch (e: Exception) {
            Log.e("SleepStageClassifier", "모델 인스턴스 초기화 에러", e)
            close()
        }
    }
}