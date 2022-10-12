package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        on { lookup(tenantId, listOf(publicKeyOne.publicKeyId())) } doReturn listOf(signingKey)
        on { lookup(tenantId, listOf(publicKeyTwo.publicKeyId())) } doReturn emptyList()
    }

    private val extractor = KeySpecExtractor(tenantId, cryptoOpsClient)

    @Test
    fun `valid key returns the correct spec`() {
        assertThat(extractor.getSpec(publicKeyOne)).isEqualTo(SignatureSpec.ECDSA_SHA256)
    }

    @Test
    fun `unknown key throws an exception`() {
        assertThrows<CordaRuntimeException> {
            extractor.getSpec(publicKeyTwo)
        }
    }
}
