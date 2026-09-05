package com.example.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 隐私模式管理器（第七轮第 6.4 条）。
 *
 * - 全局 6 位数字 PIN：首次开启隐私模式时设置（输入 + 二次确认）；
 * - 存储做基本安全处理：随机盐 + SHA-256 哈希，不落明文（补充说明第 3 条）；
 * - 开关状态持久化——重启 App 后受保护分类仍需 PIN 验证；
 * - 无痕浏览（6.5）语义由 MainViewModel 按"隐私模式开启 且 书籍所在分类受保护"
 *   判定，本类只提供开关与校验。
 */
class PrivacyManager(context: Context) {

    private val prefs = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENABLED = "privacy_mode_enabled"
        private const val KEY_PIN_HASH = "privacy_pin_hash"
        private const val KEY_PIN_SALT = "privacy_pin_salt"
        private const val PIN_LENGTH = 6

        fun pinLength(): Int = PIN_LENGTH

        /** SHA-256(盐 + PIN) 十六进制 */
        internal fun hashPin(pin: String, saltHex: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            digest.update(salt)
            digest.update(pin.toByteArray(Charsets.UTF_8))
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        internal fun newSaltHex(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    /** 隐私模式是否开启 */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /** 是否已设置过 PIN（用于区分"首次开启"与"后续验证"） */
    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    /**
     * 设置 PIN 并启用隐私模式（首次开启流程）。
     * @return false = PIN 格式非法（非 6 位数字）
     */
    fun enableWithPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = newSaltHex()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .putBoolean(KEY_ENABLED, true)
            .apply()
        return true
    }

    /** 验证 PIN（常数时间比较防时序侧信道的基本形态） */
    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val candidate = hashPin(pin, salt)
        if (candidate.length != stored.length) return false
        var diff = 0
        for (i in candidate.indices) diff = diff or (candidate[i].code xor stored[i].code)
        return diff == 0
    }

    /** 修改 PIN（需先验证旧 PIN） */
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin) || !isValidPin(newPin)) return false
        val salt = newSaltHex()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(newPin, salt))
            .apply()
        return true
    }

    /** 关闭隐私模式（需先验证 PIN；分类保护标记保留在 DB，开关关闭期间不生效） */
    fun disable(pin: String): Boolean {
        if (!verifyPin(pin)) return false
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        return true
    }

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it.isDigit() }
}
