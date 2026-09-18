package io.github.mimai114514.chemeilai.core

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CheLaileCrypto {

    private const val SALT = "qwihrnbtmj"
    private const val AES_KEY_TEXT = "422556651C7F7B2B5C266EED06068230"
    private val aesKey = AES_KEY_TEXT.toByteArray(Charsets.UTF_8)

    fun cryptoSign(params: List<Pair<String, Any?>>): String {
        val raw = buildString {
            var first = true
            for ((key, value) in params) {
                if (value == null) continue
                if (!first) append('&')
                append(key).append('=').append(value)
                first = false
            }
        }
        return md5Hex(raw + SALT)
    }

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun decryptAesEcbBase64(cipherText: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"))
        val decoded = Base64.getMimeDecoder().decode(cipherText)
        return String(cipher.doFinal(decoded), Charsets.UTF_8)
    }

    fun stripMarkers(raw: String): String {
        var text = raw.trim()
        if (text.startsWith(PREFIX)) text = text.removePrefix(PREFIX)
        if (text.endsWith(SUFFIX)) text = text.removeSuffix(SUFFIX)
        return text
    }

    private const val PREFIX = "**YGKJ"
    private const val SUFFIX = "YGKJ##"
}
