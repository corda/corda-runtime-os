package net.corda.flow.application.crypto

import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.failures.CryptoSignatureException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DigitalSignatureVerificationServiceImplTest {

    private val signatureService = mock<SignatureVerificationService>()
    private val signingService = DigitalSignatureVerificationServiceImpl(signatureService)

    @Test
    fun `verify calls SignatureService#verify`() {
        signingService.verify(mock(), mock(), byteArrayOf(), byteArrayOf())
        verify(signatureService).verify(any(), any<SignatureSpec>(), any(), any())
    }

    @Test
    fun `verify throws exception when SignatureService#verify throws`() {
        whenever(signatureService.verify(any(), any<SignatureSpec>(), any(), any())).thenThrow(CryptoSignatureException("oops!"))
        assertThrows<CryptoSignatureException> { signingService.verify(mock(), mock(), byteArrayOf(), byteArrayOf()) }
    }
}