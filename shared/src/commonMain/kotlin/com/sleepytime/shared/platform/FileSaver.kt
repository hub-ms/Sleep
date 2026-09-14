package com.sleepytime.shared.platform

interface FileSaver {
    fun saveText(fileName: String, content: String)
}