package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.PersistEntities
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PersistExternalEventFactoryTest {

    @Test
    fun `creates a record containing an EntityRequest with a PersistEntities payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext("request id", "flow id")

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = PersistExternalEventFactory().createExternalEvent(
            checkpoint,
            externalEventContext,
            PersistParameters(ByteBuffer.wrap(byteArrayOf(1)))
        )
        assertEquals(Schemas.VirtualNode.ENTITY_PROCESSOR, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            EntityRequest(
                ALICE_X500_HOLDING_IDENTITY,
                PersistEntities(listOf(ByteBuffer.wrap(byteArrayOf(1)))),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}