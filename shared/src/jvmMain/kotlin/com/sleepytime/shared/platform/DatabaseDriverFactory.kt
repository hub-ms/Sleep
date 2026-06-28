package com.sleepytime.shared.platform

import app.cash.sqldelight.db.SqlDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return TODO("Provide an actual implementation for iOS")
    }
}