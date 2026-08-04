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

fun createTafakkurDb(factory: DatabaseDriverFactory): TafakkurDb =
    TafakkurDb(
        driver = factory.createDriver(),
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
