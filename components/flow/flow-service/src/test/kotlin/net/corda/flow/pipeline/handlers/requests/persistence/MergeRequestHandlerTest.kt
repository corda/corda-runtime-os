package net.corda.flow.pipeline.handlers.requests.persistence

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.test.utils.mockPersistenceManagerAndStubContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MergeRequestHandlerTest {

    @Test
    fun `Generate merge request`() {
        val (mockPersistenceManager, mockContext) = mockPersistenceManagerAndStubContext<Any>(Wakeup(), PersistenceState())

        val requestHandler = MergeRequestHandler(mockPersistenceManager)
        val mergeRequest = FlowIORequest.Merge("requestId", "bytes".toByteArray())
        requestHandler.postProcess(mockContext, mergeRequest)

        verify(mockPersistenceManager, times(1)).processMessageToSend(any(), any())
    }
}
