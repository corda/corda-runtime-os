package net.corda.flow.pipeline.handlers.events

import java.time.Instant
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.flow.test.utils.mockPersistenceManagerAndStubContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class EntityResponseHandlerTest {

    @Test
    fun `PersistenceState was null`() {
        val instant = Instant.now()
        val response = EntityResponse(instant, "request1", EntityResponseSuccess(emptyList()))
        val (mockPersistenceManager, mockContext) = mockPersistenceManagerAndStubContext(response)

        val entityResponseHandler = EntityResponseHandler(mockPersistenceManager)
        entityResponseHandler.preProcess(mockContext)

        verify(mockPersistenceManager, times(0)).processMessageReceived(anyOrNull(), any())
    }

    @Test
    fun `Process entity response`() {
        val instant = Instant.now()
        val persistenceState = PersistenceState("request1", instant, EntityRequest(), 0, null)
        val response = EntityResponse(instant, "request1", EntityResponseSuccess(emptyList()))
        val (mockDbManager, stubContext) = mockPersistenceManagerAndStubContext(response, persistenceState)

        stubContext.inputEventPayload = response

        val entityResponseHandler = EntityResponseHandler(mockDbManager)
        entityResponseHandler.preProcess(stubContext)

        verify(mockDbManager, times(1)).processMessageReceived(any(), any())

    }
}