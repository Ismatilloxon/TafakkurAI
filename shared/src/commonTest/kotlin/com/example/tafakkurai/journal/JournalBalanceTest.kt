package com.example.tafakkurai.journal

import com.example.tafakkurai.balance.BalanceInput
import com.example.tafakkurai.balance.computeBalance
import kotlin.test.Test
import kotlin.test.assertTrue

class JournalBalanceTest {

    @Test
    fun aCompletedJournalProjectionMovesSpiritualBalanceFromRecordedData() {
        val before = computeBalance(BalanceInput()).first { it.key == "spiritual" }.value!!
        val after = computeBalance(BalanceInput(spiritualActivitiesToday = 1))
            .first { it.key == "spiritual" }
            .value!!

        assertTrue(after > before)
    }
}
