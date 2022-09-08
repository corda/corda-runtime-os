package net.corda.flow.application.persistence.external.events

import net.corda.data.KeyValuePairList
import java.nio.ByteBuffer
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.MergeEntities
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MergeExternalEventFactoryTest {

    @Test
    fun `creates a record containing an EntityRequest with a MergeEntities payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = MergeExternalEventFactory().createExternalEvent(
            checkpoint,
            externalEventContext,
            MergeParameters(listOf(ByteBuffer.wrap(byteArrayOf(1))))
        )
        assertEquals(Schemas.VirtualNode.ENTITY_PROCESSOR, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            EntityRequest(
                ALICE_X500_HOLDING_IDENTITY,
                MergeEntities(listOf(ByteBuffer.wrap(byteArrayOf(1)))),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}