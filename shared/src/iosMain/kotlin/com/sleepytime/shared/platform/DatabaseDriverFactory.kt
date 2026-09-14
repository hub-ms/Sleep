package com.sleepytime.shared.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.sleepytime.shared.data.local.generated.SleepDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(SleepDatabase.Schema, "sleep_database.db")
    }
}