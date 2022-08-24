package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AbstractPersistenceExternalEventFactoryTest {

    private companion object {
        const val PAYLOAD = "payload"
    }

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
        val externalEventContext = ExternalEventContext("request id", "flow id")

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = abstractPersistenceExternalEventFactory.createExternalEvent(
            checkpoint,
            externalEventContext,
            PAYLOAD
        )
        assertEquals(Schemas.VirtualNode.ENTITY_PROCESSOR, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            EntityRequest(
                ALICE_X500_HOLDING_IDENTITY,
                PAYLOAD,
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
            EntityResponse(results)
        )
        assertEquals(results, resume)
    }
}