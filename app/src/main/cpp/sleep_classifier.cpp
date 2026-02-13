//
// Created by essentialism on 2025-10-03.
//

#include "sleep_classifier.h"
#include <jni.h>
#include <string>
#include <vector>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#define LOG_TAG "SleepClassifierJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    std::unique_ptr<SleepClassifier> sleepClassifier;
}

// C++ Implementation of SleepClassifier
SleepClassifier::SleepClassifier(const void* model_buffer, size_t model_size) {
    model_ = tflite::FlatBufferModel::BuildFromBuffer(static_cast<const char*>(model_buffer), model_size);
    if (!model_) {
        LOGE("Failed to build model from buffer.");
        return;
    }

    tflite::ops::builtin::BuiltinOpResolver resolver;
    tflite::InterpreterBuilder(*model_, resolver)(&interpreter_);
    if (!interpreter_) {
        LOGE("Failed to build interpreter.");
        return;
    }

    if (interpreter_->AllocateTensors() != kTfLiteOk) {
        LOGE("Failed to allocate tensors.");
        return;
    }

    input_tensor_ = interpreter_->input_tensor(0);
    output_tensor_ = interpreter_->output_tensor(0);

    initialized_ = true;
    LOGI("TFLite C++ Classifier initialized successfully");
}

SleepClassifier::~SleepClassifier() {
    LOGI("TFLite C++ Classifier destroyed");
}

int SleepClassifier::classify(const std::vector<float>& input_data) {
    if (!initialized_) {
        LOGE("Classifier is not initialized.");
        return -1;
    }

    // Check input size
    if (input_data.size() * sizeof(float) != input_tensor_->bytes) {
        LOGE("Input data size (%zu) does not match model input tensor size (%zu).", input_data.size() * sizeof(float), input_tensor_->bytes);
        return -1;
    }

    memcpy(input_tensor_->data.f, input_data.data(), input_tensor_->bytes);

    if (interpreter_->Invoke() != kTfLiteOk) {
        LOGE("Failed to invoke interpreter");
        return -1;
    }

    float* output = output_tensor_->data.f;
    int num_classes = output_tensor_->dims->data[1];

    int max_index = -1;
    float max_prob = -1.0f;
    for (int i = 0; i < num_classes; ++i) {
        if (output[i] > max_prob) {
            max_prob = output[i];
            max_index = i;
        }
    }
    return max_index;
}


// JNI part
extern "C" JNIEXPORT jint JNICALL
Java_com_example_sleep_SleepStageClassifier_initClassifier(
        JNIEnv *env,
        jobject /* this */,
        jobject assetManager) {

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    AAsset* asset = AAssetManager_open(mgr, "sleep_stage_model.tflite", AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Failed to open model file.");
        return -1;
    }

    const void* model_buffer = AAsset_getBuffer(asset);
    size_t model_size = AAsset_getLength(asset);

    sleepClassifier = std::make_unique<SleepClassifier>(model_buffer, model_size);

    AAsset_close(asset);

    if (!sleepClassifier || !sleepClassifier->isInitialized()) {
        LOGE("Failed to initialize classifier.");
        return -2;
    }

    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_sleep_SleepStageClassifier_classify(
        JNIEnv *env,
        jobject /* this */,
        jfloatArray input_data) {

    if (!sleepClassifier || !sleepClassifier->isInitialized()) {
        LOGE("Classifier not initialized. Call initClassifier first.");
        return -1;
    }

    jsize len = env->GetArrayLength(input_data);
    std::vector<float> native_input_data(len);
    env->GetFloatArrayRegion(input_data, 0, len, native_input_data.data());

    return sleepClassifier->classify(native_input_data);
}