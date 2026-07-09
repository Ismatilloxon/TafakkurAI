package com.example.tafakkurai.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createTafakkurDb(factory: DatabaseDriverFactory): TafakkurDb =
    TafakkurDb(factory.createDriver())
