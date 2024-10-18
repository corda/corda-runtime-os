package net.corda.flow.application.persistence.external.events

import net.corda.data.persistence.DeleteEntities
import net.corda.data.persistence.EntityRequest
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.state.FlowCheckpoint
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import net.corda.flow.external.events.ExternalEventContext
import net.corda.flow.utils.toAvro

class RemoveExternalEventFactoryTest {

    @Test
    fun `creates a record containing an EntityRequest with a DeleteEntities payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext("request id", "flow id", emptyMap())

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = RemoveExternalEventFactory().createExternalEvent(
            checkpoint,
            externalEventContext,
            RemoveParameters(listOf(byteArrayOf(1)))
        )
        assertNull(externalEventRecord.key)
        assertEquals(
            EntityRequest(
                ALICE_X500_HOLDING_IDENTITY,
                DeleteEntities(listOf(ByteBuffer.wrap(byteArrayOf(1)))),
                externalEventContext.toAvro()
            ),
            externalEventRecord.payload
        )
    }
}
