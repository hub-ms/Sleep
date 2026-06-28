package com.sleepytime.shared.platform

import android.content.Context
import java.io.File

class AndroidFileSaver(private val context: Context) : FileSaver {
    override fun saveText(fileName: String, content: String) {
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(content)
    }
}