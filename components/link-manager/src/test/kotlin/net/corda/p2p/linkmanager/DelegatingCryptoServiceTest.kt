package net.corda.p2p.linkmanager

import net.corda.crypto.client.CryptoOpsClient
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey

class DelegatingCryptoServiceTest {

    companion object {
        private val SIGNATURE_BYTES = "SIGNED_DATA".toByteArray()
    }

    private val signature = mock<DigitalSignature.WithKey> {
        on { bytes } doReturn ( SIGNATURE_BYTES )
    }
    private val cryptoClient = mock<CryptoOpsClient> {
        on { sign(any(), any(), any<SignatureSpec>(), any(), eq(CryptoOpsClient.EMPTY_CONTEXT)) }.doReturn(signature)
    }
    private val delegatingCryptoService = DelegatingCryptoService(cryptoClient)

    @Test
    fun `Delegating crypto service delegates signing requests to the crypto service`() {
        val tenantId = "myTenant"
        val key = mock<PublicKey>()
        val signatureSpec = mock<SignatureSpec>()
        val data = "DATA".toByteArray()
        val signature = delegatingCryptoService.sign(tenantId, key, signatureSpec, data)
        assertThat(signature).isEqualTo(SIGNATURE_BYTES)
    }
}