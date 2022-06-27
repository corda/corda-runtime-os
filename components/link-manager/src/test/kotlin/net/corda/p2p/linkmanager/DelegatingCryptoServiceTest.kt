package net.corda.p2p.linkmanager

import net.corda.crypto.client.CryptoOpsClient
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.PublicKey

class DelegatingCryptoServiceTest {

    private val signature = mock<DigitalSignature.WithKey> {
        on { bytes } doReturn ( "SIGNED_DATA".toByteArray() )
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
        delegatingCryptoService.sign(tenantId, key, signatureSpec, data)
        verify(cryptoClient).sign(tenantId, key, signatureSpec, data)
    }
}