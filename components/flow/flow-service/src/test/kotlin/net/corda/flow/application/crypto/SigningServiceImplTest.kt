package net.corda.flow.application.crypto

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SigningServiceImplTest {

    private val keyEncodingService = mock<KeyEncodingService>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val signingService = SigningServiceImpl(externalEventExecutor, keyEncodingService)

    @Test
    fun `sign returns the signature returned from the flow resuming`() {
        val signature = DigitalSignature.WithKey(mock(), byteArrayOf(1), emptyMap())
        whenever(externalEventExecutor.execute(eq(CreateSignatureExternalEventHandler::class.java), any()))
            .thenReturn(signature)
        assertEquals(signature, signingService.sign(byteArrayOf(1), mock(), mock()))
    }
}