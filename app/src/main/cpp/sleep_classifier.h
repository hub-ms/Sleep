//
// Created by essentialism on 2025-10-03.
//

#ifndef SLEEP_SLEEP_CLASSIFIER_H
#define SLEEP_SLEEP_CLASSIFIER_H

#include <string>
#include <memory>
#include <vector>
#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/kernels/register.h"

class SleepClassifier {
public:
    explicit SleepClassifier(const void* model_buffer, size_t model_size);

    ~SleepClassifier();

    int classify(const std::vector<float>& input_data);
    bool isInitialized() const { return initialized_; }
private:
    std::unique_ptr<tflite::FlatBufferModel> model_;
    std::unique_ptr<tflite::Interpreter> interpreter_;

    TfLiteTensor* input_tensor_ = nullptr;
    TfLiteTensor* output_tensor_ = nullptr;
    bool initialized_ = false;
};

#endif //SLEEP_SLEEP_CLASSIFIER_H
