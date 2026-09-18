package io.github.mimai114514.chemeilai.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CheLaileCryptoTest {

    @Test
    fun `cryptoSign matches reference algorithm`() {
        val sign = CheLaileCrypto.cryptoSign(listOf("cityState" to "2"))
        assertEquals("0a760cd23cc9fe6d33cc0e717c738f2f", sign)
    }

    @Test
    fun `cryptoSign preserves parameter order`() {
        val ordered = CheLaileCrypto.cryptoSign(
            listOf(
                "lineId" to "0751131909628",
                "lineName" to "7",
                "direction" to 1,
                "targetOrder" to 8,
            ),
        )
        val reordered = CheLaileCrypto.cryptoSign(
            listOf(
                "targetOrder" to 8,
                "direction" to 1,
                "lineName" to "7",
                "lineId" to "0751131909628",
            ),
        )
        assertEquals(true, ordered != reordered)
    }

    @Test
    fun `stripMarkers removes YGKJ envelope`() {
        assertEquals("{\"a\":1}", CheLaileCrypto.stripMarkers("**YGKJ{\"a\":1}YGKJ##"))
        assertEquals("{\"a\":1}", CheLaileCrypto.stripMarkers("  {\"a\":1}  "))
    }

    @Test
    fun `decryptAesEcbBase64 round trips known payload`() {
        val plain = "{\"status\":\"ok\"}"
        val encrypted = encryptForTest(plain)
        assertEquals(plain, CheLaileCrypto.decryptAesEcbBase64(encrypted))
    }

    private fun encryptForTest(plain: String): String {
        val key = "422556651C7F7B2B5C266EED06068230".toByteArray(Charsets.UTF_8)
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
        )
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
}
