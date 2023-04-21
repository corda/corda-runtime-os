package net.corda.flow.messaging

import net.corda.flow.application.messaging.ExternalMessagingImpl
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.fiber.FlowIORequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify

class ExternalMessagingImplTest {

    private val flowFiberService = MockFlowFiberService()
    private val target = ExternalMessagingImpl(flowFiberService) { "random_id" }

    @Test
    fun `send channel and message issues IO request`() {
        target.send("ch1", "msg1")

        val expectedIoRequest = FlowIORequest.SendExternalMessage(
            "ch1",
            "random_id",
            "msg1"
        )
        verify(flowFiberService.flowFiber).suspend(expectedIoRequest)
    }

    @Test
    fun `send channel, message id and message issues IO request`() {
        target.send("ch1", "id2", "msg1")

        val expectedIoRequest = FlowIORequest.SendExternalMessage("ch1", "id2", "msg1")
        verify(flowFiberService.flowFiber).suspend(expectedIoRequest)
    }
}
