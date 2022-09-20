package net.corda.flow.application.crypto

import java.security.PublicKey
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.crypto.DigitalSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SigningServiceImplTest {
    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val captor = argumentCaptor<SignParameters>()
    private val signingService = SigningServiceImpl(externalEventExecutor)

    @Test
    fun `sign returns the signature returned from the flow resuming`() {
        val signature = DigitalSignature.WithKey(mock(), byteArrayOf(1), emptyMap())
        val publicKey = mock<PublicKey>()
        whenever(externalEventExecutor.execute(eq(CreateSignatureExternalEventFactory::class.java), captor.capture()))
            .thenReturn(signature)
        assertEquals(signature, signingService.sign(byteArrayOf(1), publicKey, mock()))
    }
}