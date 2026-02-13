cmake_minimum_required(VERSION 3.22.1)
project("sleep")
add_library(sleep SHARED sleep_classifier.cpp)
target_include_directories(sleep PRIVATE ${CMAKE_SOURCE_DIR}/libs/include)
target_link_libraries(
  sleep
  ${CMAKE_SOURCE_DIR}/libs/arm64-v8a/libtensorflowlite.so
  android
  log
)
