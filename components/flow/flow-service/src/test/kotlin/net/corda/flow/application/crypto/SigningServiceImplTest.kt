package net.corda.flow.application.crypto

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SigningServiceImplTest {

    private val flowFiber = mock<FlowFiber>()
    private val flowFiberService = mock<FlowFiberService>()
    private val keyEncodingService = mock<KeyEncodingService>()
    private val signingService = SigningServiceImpl(flowFiberService, keyEncodingService)

    @Test
    fun `sign returns the signature returned from the flow resuming`() {
        val signature = DigitalSignature.WithKey(mock(), byteArrayOf(1), emptyMap())
        whenever(flowFiber.suspend(any<FlowIORequest.SignBytes>())).thenReturn(signature)
        whenever(flowFiberService.getExecutingFiber()).thenReturn(flowFiber)
        assertEquals(signature, signingService.sign(byteArrayOf(1), mock(), mock()))
    }
}