package net.corda.flow.pipeline.handlers.requests.persistence

import net.corda.data.flow.event.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.test.utils.mockDbManagerAndStubContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class DeleteRequestHandlerTest {

    @Test
    fun `Generate delete request`() {
        val (mockDbManager, mockContext) = mockDbManagerAndStubContext<Any>(Wakeup())

        val requestHandler = DeleteRequestHandler(mockDbManager)
        val deleteRequest = FlowIORequest.Delete("requestId", "bytes".toByteArray())
        requestHandler.postProcess(mockContext, deleteRequest)

        verify(mockDbManager, times(1)).processMessageToSend(any(), any())
    }
}
