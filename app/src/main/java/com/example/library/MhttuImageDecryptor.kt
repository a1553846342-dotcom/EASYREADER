package com.example.library

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 漫蛙吧图床（tu.mhttu.cc）图片解密。
 * 站点前端逻辑：AES-256-CBC，密钥 = "0B6666A0-BB59-1381-B746-a0E4C9AC" 前 32 字节，
 * IV = 文件前 16 字节，密文 = 文件第 17 字节起；已解出的为 WebP/JPEG 图片。
 */
object MhttuImageDecryptor {

    private val keyBytes: ByteArray =
        "0B6666A0-BB59-1381-B746-a0E4C9AC".toByteArray(Charsets.UTF_8).copyOf(32)

    fun isEncryptedHost(host: String): Boolean =
        host == "tu.mhttu.cc" || host == "mhttu.cc" || host.endsWith(".mhttu.cc")

    fun decryptIfNeeded(data: ByteArray): ByteArray {
        if (data.size <= 16 || isImageBytes(data)) return data
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                IvParameterSpec(data.copyOfRange(0, 16))
            )
            cipher.doFinal(data.copyOfRange(16, data.size))
        } catch (e: Exception) {
            data
        }
    }

    fun imageExtension(data: ByteArray): String? = when {
        data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> "jpg"
        data.size >= 4 &&
            data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x46.toByte() -> "webp"
        data.size >= 4 &&
            data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() -> "png"
        else -> null
    }

    private fun isImageBytes(data: ByteArray): Boolean = imageExtension(data) != null
}
