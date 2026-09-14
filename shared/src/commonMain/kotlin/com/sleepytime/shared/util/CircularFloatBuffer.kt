package com.sleepytime.shared.util


class CircularFloatBuffer(private val capacity: Int) {
    private val arr = FloatArray(capacity)
    private var head = 0
    private var size = 0

    fun add(value: Float) {
        val idx = (head + size) % capacity
        if(size < capacity) {
            arr[idx] = value
            size++
        } else {
            arr[head] = value
            head = (head + 1) % capacity
        }
    }
    fun toList(): List<Float> {
        val list = ArrayList<Float>(size)
        for(i in 0..size) {
            list.add(arr[(head + i) % capacity])
        }
        return list
    }
    fun clear() {
        head = 0
        size = 0
    }
}
