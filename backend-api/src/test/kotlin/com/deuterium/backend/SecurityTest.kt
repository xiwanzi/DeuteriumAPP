package com.deuterium.backend

import com.deuterium.backend.util.Secrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityTest {
    @Test
    fun `six digit code has expected format`() {
        val code = Secrets.sixDigitCode()
        assertTrue(Regex("^\\d{6}$").matches(code))
    }

    @Test
    fun `hash depends on pepper`() {
        val value = "secret"
        assertEquals(Secrets.sha256(value, "pepper-a"), Secrets.sha256(value, "pepper-a"))
        assertNotEquals(Secrets.sha256(value, "pepper-a"), Secrets.sha256(value, "pepper-b"))
    }
}


