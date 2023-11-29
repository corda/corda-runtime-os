package net.corda.flow.application.persistence.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.state.FlowCheckpoint
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class AbstractPersistenceExternalEventFactoryTest {

    private val abstractPersistenceExternalEventFactory = object : AbstractPersistenceExternalEventFactory<String>() {
        override fun createRequest(parameters: String): Any {
            return parameters
        }
    }

    @Test
    fun responseType() {
        assertEquals(EntityResponse::class.java, abstractPersistenceExternalEventFactory.responseType)
    }

    @Test
    fun `creates an external event record containing an EntityRequest`() {
        val checkpoint = mock<FlowCheckpoint>()
        val payload = "payload"
        val externalEventContext = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = abstractPersistenceExternalEventFactory.createExternalEvent(
            checkpoint,
            externalEventContext,
            payload
        )
        assertNull(externalEventRecord.key)
        assertEquals(
            EntityRequest(
                ALICE_X500_HOLDING_IDENTITY,
                payload,
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }

    @Test
    fun resumeWith() {
        val results = listOf(ByteBuffer.wrap(byteArrayOf(1, 2, 3)))
        val resume = abstractPersistenceExternalEventFactory.resumeWith(
            mock(),
            EntityResponse(results, KeyValuePairList(emptyList()), null)
        )
        assertEquals(results, resume)
    }
}
