package com.deuterium.backend.util

import de.mkammerer.argon2.Argon2Factory
import java.security.MessageDigest
import java.security.SecureRandom

class PasswordHasher {
    private val argon2 = Argon2Factory.create()

    fun hash(password: String): String =
        argon2.hash(3, 65536, 2, password.toCharArray())

    fun verify(hash: String, password: String): Boolean =
        argon2.verify(hash, password.toCharArray())
}

object Secrets {
    private val random = SecureRandom()

    fun sixDigitCode(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    fun sha256(value: String, pepper: String = ""): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((pepper + ":" + value).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}


