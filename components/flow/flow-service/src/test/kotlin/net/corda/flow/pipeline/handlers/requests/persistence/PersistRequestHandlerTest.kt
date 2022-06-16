package net.corda.flow.pipeline.handlers.requests.persistence

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.test.utils.mockPersistenceManagerAndStubContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class PersistRequestHandlerTest {

    @Test
    fun `Generate persist request`() {
        val (mockPersistenceManager, mockContext) = mockPersistenceManagerAndStubContext<Any>(Wakeup(), PersistenceState())

        val requestHandler = PersistRequestHandler(mockPersistenceManager)
        val persistRequest = FlowIORequest.Persist("requestId", "bytes".toByteArray())
        requestHandler.postProcess(mockContext, persistRequest)

        verify(mockPersistenceManager, times(1)).processMessageToSend(any(), any())
    }
}
