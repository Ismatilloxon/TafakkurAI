package com.example.tafakkurai.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.ColumnAdapter

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

private val intAdapter = object : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()
    override fun encode(value: Int): Long = value.toLong()
}

/**
 * Builds the database from an already-created driver.
 *
 * The driver is constructed separately and injected because the sync platform needs it
 * directly: discovering tables, installing triggers and assembling payloads are raw SQL
 * against whatever schema exists, which SQLDelight's generated API — typed against the
 * schema known at compile time — cannot express. Everything else keeps using [TafakkurDb].
 */
fun createTafakkurDb(driver: SqlDriver): TafakkurDb =
    TafakkurDb(
        driver = driver,
        hifzCardAdapter = HifzCard.Adapter(
            surahAdapter = intAdapter,
            ayahAdapter = intAdapter,
            stateAdapter = intAdapter,
            repsAdapter = intAdapter,
            lapsesAdapter = intAdapter,
            lastRatingAdapter = intAdapter
        ),
        hifzReviewAdapter = HifzReview.Adapter(
            surahAdapter = intAdapter,
            ayahAdapter = intAdapter,
            ratingAdapter = intAdapter
        )
    )

/** Convenience for callers that do not need the driver themselves. */
fun createTafakkurDb(factory: DatabaseDriverFactory): TafakkurDb =
    createTafakkurDb(factory.createDriver())
