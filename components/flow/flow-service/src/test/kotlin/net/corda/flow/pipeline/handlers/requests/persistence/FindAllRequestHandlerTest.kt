package net.corda.flow.pipeline.handlers.requests.persistence

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.test.utils.mockPersistenceManagerAndStubContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FindAllRequestHandlerTest {

    @Test
    fun `Generate findAll request`() {
        val (mockPersistenceManager, mockContext) = mockPersistenceManagerAndStubContext<Any>(
            Wakeup(),
            PersistenceState()
        )

        val requestHandler = FindAllRequestHandler(mockPersistenceManager)
        val findAllRequest = FlowIORequest.FindAll("requestId", "className")
        requestHandler.postProcess(mockContext, findAllRequest)

        verify(mockPersistenceManager, times(1)).processMessageToSend(any(), any())
    }
}
