package com.soundsleeper.app.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.soundsleeper.app.data.local.generated.SleepDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(SleepDatabase.Schema, context, "sleep_database.db")
    }
}