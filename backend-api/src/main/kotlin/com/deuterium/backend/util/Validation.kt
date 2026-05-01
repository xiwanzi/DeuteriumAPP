package com.deuterium.backend.util

import com.deuterium.backend.web.ApiException
import java.math.BigDecimal
import java.math.RoundingMode

object Validation {
    private val codeRegex = Regex("^\\d{6}$")
    private val amountRegex = Regex("^\\d+(\\.\\d{1,2})?$")

    fun gameId(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 32) {
            throw ApiException.invalid("INVALID_REQUEST", "请输入有效的游戏内 ID。")
        }
        return trimmed
    }

    fun qq(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 20) {
            throw ApiException.invalid("INVALID_REQUEST", "请输入有效的 QQ 号。")
        }
        return trimmed
    }

    fun password(value: String, fieldName: String = "密码"): String {
        if (value.length !in 8..64) {
            throw ApiException.invalid("PASSWORD_INVALID", "$fieldName 必须为 8-64 位。", 422)
        }
        return value
    }

    fun code(value: String): String {
        val trimmed = value.trim()
        if (!codeRegex.matches(trimmed)) {
            throw ApiException.invalid("VERIFICATION_INVALID", "验证码错误。", 422)
        }
        return trimmed
    }

    fun amount(value: String): BigDecimal {
        val trimmed = value.trim()
        if (!amountRegex.matches(trimmed)) {
            throw ApiException.invalid("AMOUNT_INVALID", "金额必须大于 0，且最多两位小数。", 422)
        }
        val amount = trimmed.toBigDecimal().setScale(2, RoundingMode.UNNECESSARY)
        if (amount <= BigDecimal.ZERO) {
            throw ApiException.invalid("AMOUNT_INVALID", "金额必须大于 0，且最多两位小数。", 422)
        }
        return amount
    }

    fun note(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > 80) {
            throw ApiException.invalid("INVALID_REQUEST", "备注不能超过 80 字符。")
        }
        return trimmed
    }

    fun chatContent(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw ApiException.invalid("CHAT_MESSAGE_EMPTY", "消息不能为空。", 422)
        }
        if (trimmed.length > 256) {
            throw ApiException.invalid("CHAT_MESSAGE_TOO_LONG", "消息不能超过 256 字符。", 422)
        }
        return trimmed
    }
}


