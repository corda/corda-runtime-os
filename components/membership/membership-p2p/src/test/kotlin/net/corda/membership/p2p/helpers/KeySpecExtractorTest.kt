package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.membership.p2p.helpers.KeySpecExtractor.Companion.validateSchemeAndSignatureSpec
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.KeySchemeCodes
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
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
        assertThat(extractor.getSpec(publicKeyOne)).isEqualTo(SignatureSpecs.ECDSA_SHA256)
    }

    @Test
    fun `unknown key throws an exception`() {
        val exception = assertThrows<CordaRuntimeException> {
            extractor.getSpec(publicKeyTwo)
        }
        assertThat(exception).hasMessageContaining("Public key is not owned by $tenantId")
    }

    @Test
    fun `validateSchemeAndSignatureSpec throw exception for invalid schemeCodeName`() {
        val key = mock<CryptoSigningKey> {
            on { schemeCodeName } doReturn "nop"
        }

        val exception = assertThrows<IllegalArgumentException> {
            key.validateSchemeAndSignatureSpec(SignatureSpecs.ECDSA_SHA256.signatureName)
        }
        assertThat(exception).hasMessageContaining("Invalid key scheme")
    }

    @Test
    fun `validateSchemeAndSignatureSpec throws exception for invalid schemeCodeName for session key`() {
        val key = mock<CryptoSigningKey> {
            on { schemeCodeName } doReturn KeySchemeCodes.EDDSA_ED25519_CODE_NAME
        }

        val exception = assertThrows<IllegalArgumentException> {
            key.validateSchemeAndSignatureSpec(SignatureSpecs.EDDSA_ED25519.signatureName, KeySpecExtractor.KeySpecType.SESSION)
        }
        assertThat(exception).hasMessageContaining("Invalid key scheme")
    }

    @Test
    fun `validateSchemeAndSignatureSpec throws exception for invalid spec name for session key`() {
        val exception = assertThrows<IllegalArgumentException> {
            signingKey.validateSchemeAndSignatureSpec(SignatureSpecs.EDDSA_ED25519.signatureName, KeySpecExtractor.KeySpecType.SESSION)
        }
        assertThat(exception).hasMessageContaining("Invalid key spec ${SignatureSpecs.EDDSA_ED25519.signatureName}.")
    }

    @Test
    fun `validateSchemeAndSignatureSpec throw exception for invalid spec name`() {
        val exception = assertThrows<IllegalArgumentException> {
            signingKey.validateSchemeAndSignatureSpec(SignatureSpecs.RSA_SHA512.signatureName)
        }
        assertThat(exception).hasMessageContaining("Invalid key spec ${SignatureSpecs.RSA_SHA512.signatureName}")
    }

    @Test
    fun `validateSchemeAndSignatureSpec pass with valid names`() {
        assertDoesNotThrow {
            signingKey.validateSchemeAndSignatureSpec(SignatureSpecs.ECDSA_SHA256.signatureName)
        }
    }

    @Test
    fun `validateSchemeAndSignatureSpec passes for valid key scheme when no signature spec specified`() {
        assertDoesNotThrow {
            signingKey.validateSchemeAndSignatureSpec(null)
        }
    }

    @Test
    fun `validateSchemeAndSignatureSpec throw exception for invalid schemeCodeName when no signature spec specified`() {
        val key = mock<CryptoSigningKey> {
            on { schemeCodeName } doReturn "nop"
        }

        val exception = assertThrows<IllegalArgumentException> {
            key.validateSchemeAndSignatureSpec(null)
        }
        assertThat(exception).hasMessageContaining("Invalid key scheme")
    }
}
