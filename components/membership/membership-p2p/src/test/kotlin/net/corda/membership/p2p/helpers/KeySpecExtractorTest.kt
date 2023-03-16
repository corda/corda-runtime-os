package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.membership.p2p.helpers.KeySpecExtractor.Companion.validateSpecName
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey

class KeySpecExtractorTest {
    private val tenantId = "abcd"
    private val publicKeyOne = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(1, 2, 3)
    }
    private val publicKeyTwo = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(4, 5, 6)
    }
    private val signingKey = mock<CryptoSigningKey> {
        on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
    }
    private val cryptoOpsClient = mock<CryptoOpsClient> {
        on { lookupKeysByIds(tenantId, listOf(ShortHash.of(publicKeyOne.publicKeyId()))) } doReturn listOf(signingKey)
        on { lookupKeysByIds(tenantId, listOf(ShortHash.of(publicKeyTwo.publicKeyId()))) } doReturn emptyList()
    }

    private val extractor = KeySpecExtractor(tenantId, cryptoOpsClient)

    @Test
    fun `valid key returns the correct spec`() {
        assertThat(extractor.getSpec(publicKeyOne)).isEqualTo(SignatureSpec.ECDSA_SHA256)
    }

    @Test
    fun `unknown key throws an exception`() {
        val exception = assertThrows<CordaRuntimeException> {
            extractor.getSpec(publicKeyTwo)
        }
        assertThat(exception).hasMessageContaining("Public key is not owned by $tenantId")
    }

    @Test
    fun `validateSpecName throw exception for invalid schemeCodeName`() {
        val key = mock<CryptoSigningKey> {
            on { schemeCodeName } doReturn "nop"
        }

        val exception = assertThrows<IllegalArgumentException> {
            key.validateSpecName(SignatureSpec.ECDSA_SHA256.signatureName)
        }
        assertThat(exception).hasMessageContaining("Could not identify spec for key scheme nop")
    }

    @Test
    fun `validateSpecName throw exception for invalid spec name`() {
        val exception = assertThrows<IllegalArgumentException> {
            signingKey.validateSpecName(SignatureSpec.RSA_SHA512.signatureName)
        }
        assertThat(exception).hasMessageContaining("Invalid key spec ${SignatureSpec.RSA_SHA512.signatureName}")
    }

    @Test
    fun `validateSpecName pass with valid names`() {
        assertDoesNotThrow {
            signingKey.validateSpecName(SignatureSpec.ECDSA_SHA256.signatureName)
        }
    }
}
