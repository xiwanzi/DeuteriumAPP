package com.deuterium.backend

import com.deuterium.backend.util.Validation
import com.deuterium.backend.web.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidationTest {
    @Test
    fun `amount accepts positive decimal strings with two digits`() {
        assertEquals("68.50", Validation.amount("68.50").setScale(2).toPlainString())
        assertEquals("1.00", Validation.amount("1").setScale(2).toPlainString())
    }

    @Test
    fun `amount rejects zero negative and excessive precision`() {
        assertFailsWith<ApiException> { Validation.amount("0") }
        assertFailsWith<ApiException> { Validation.amount("-1") }
        assertFailsWith<ApiException> { Validation.amount("1.234") }
    }

    @Test
    fun `chat content trims and enforces length`() {
        assertEquals("hello", Validation.chatContent("  hello  "))
        assertFailsWith<ApiException> { Validation.chatContent("   ") }
        assertFailsWith<ApiException> { Validation.chatContent("x".repeat(257)) }
    }

    @Test
    fun `password length follows account PRD`() {
        assertEquals("12345678", Validation.password("12345678"))
        assertFailsWith<ApiException> { Validation.password("1234567") }
        assertFailsWith<ApiException> { Validation.password("x".repeat(65)) }
    }
}
