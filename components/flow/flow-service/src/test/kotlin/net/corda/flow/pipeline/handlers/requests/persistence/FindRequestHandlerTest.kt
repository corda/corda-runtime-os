package net.corda.flow.pipeline.handlers.requests.persistence

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.test.utils.mockPersistenceManagerAndStubContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FindRequestHandlerTest {

    @Test
    fun `Generate find request`() {
        val (mockPersistenceManager, mockContext) = mockPersistenceManagerAndStubContext<Any>(Wakeup(), PersistenceState())

        val requestHandler = FindRequestHandler(mockPersistenceManager)
        val findRequest = FlowIORequest.Find("requestId", "className", "bytes".toByteArray())
        requestHandler.postProcess(mockContext, findRequest)

        verify(mockPersistenceManager, times(1)).processMessageToSend(any(), any())
    }
}
