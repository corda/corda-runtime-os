package net.corda.flow.application.persistence.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.FindEntities
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.state.FlowCheckpoint
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class FindExternalEventFactoryTest {

    @Test
    fun `creates a record containing an EntityRequest with a FindEntities payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = FindExternalEventFactory().createExternalEvent(
            checkpoint,
            externalEventContext,
            FindParameters(String::class.java, listOf(ByteBuffer.wrap(byteArrayOf(1))))
        )
        assertNull(externalEventRecord.key)
        assertEquals(
            EntityRequest(
                ALICE_X500_HOLDING_IDENTITY,
                FindEntities(String::class.java.canonicalName, listOf(ByteBuffer.wrap(byteArrayOf(1)))),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}