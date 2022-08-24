package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntity
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FindExternalEventFactoryTest {

    @Test
    fun `creates a record containing an EntityRequest with a FindEntity payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext("request id", "flow id")

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = FindExternalEventFactory().createExternalEvent(
            checkpoint,
            externalEventContext,
            FindParameters(String::class.java, ByteBuffer.wrap(byteArrayOf(1)))
        )
        assertEquals(Schemas.VirtualNode.ENTITY_PROCESSOR, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            EntityRequest(
                ALICE_X500_HOLDING_IDENTITY,
                FindEntity(String::class.java.canonicalName, ByteBuffer.wrap(byteArrayOf(1))),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}