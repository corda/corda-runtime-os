package net.corda.crypto

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.UUID

class AliasPrivateKeyTests {
    @Test
    @Timeout(5)
    fun `Should be able to round trip encode and decode`() {
        val alias = UUID.randomUUID().toString()
        val original = AliasPrivateKey(alias)
        val encoded = original.encoded
        val decoded = decodeAliasPrivateKey(encoded)
        assertThat(decoded, instanceOf(AliasPrivateKey::class.java))
        assertEquals(alias, (decoded as AliasPrivateKey).alias)
        assertEquals(AliasPrivateKey.ALIAS_KEY_ALGORITHM, decoded.algorithm)
        assertEquals("PKCS#8", decoded.format)
        assertEquals(original, decoded)
    }

    @Test
    @Timeout(5)
    fun `Should throw IllegalArgumentException when decoding non AliasPrivateKey`() {
        val keyPair = generateKeyPair()
        val encoded = keyPair.private.encoded
        assertThrows<IllegalArgumentException> {
            decodeAliasPrivateKey(encoded)
        }
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(3072)
        return keyPairGenerator.generateKeyPair()
    }
}