package com.example.sleep

import android.content.Context

class SleepStageClassifier(private val context: Context) {

    private var isInitialized = false

    init {
        System.loadLibrary("sleep")
        val result = initClassifier(context.assets)
        if (result == 0) {
            isInitialized = true
        }
    }

    fun classify(inputData: FloatArray): Int {
        if (!isInitialized) {
            return -1
        }
        // C++의 nativeClassify 함수 호출
        return nativeClassify(inputData)
    }

    // C++ 네이티브 함수 선언
    private external fun initClassifier(assetManager: android.content.res.AssetManager): Int
    private external fun nativeClassify(inputData: FloatArray): Int
}
