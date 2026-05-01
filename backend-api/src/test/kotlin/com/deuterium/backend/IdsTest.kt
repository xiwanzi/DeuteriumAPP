package com.deuterium.backend

import com.deuterium.backend.util.Ids
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdsTest {
    @Test
    fun `external wallet record ids are deterministic and compact`() {
        val expense = Ids.walletRecordIdFromExternal("mc_pay_abc", "expense")
        val expenseAgain = Ids.walletRecordIdFromExternal("mc_pay_abc", "expense")
        val income = Ids.walletRecordIdFromExternal("mc_pay_abc", "income")

        assertEquals(expense, expenseAgain)
        assertNotEquals(expense, income)
        assertTrue(expense.startsWith("wrec_"))
        assertTrue(expense.length <= 40)
    }
}

