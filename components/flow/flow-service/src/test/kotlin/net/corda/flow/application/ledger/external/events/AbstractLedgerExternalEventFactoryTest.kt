package net.corda.flow.application.ledger.external.events

import net.corda.data.KeyValuePairList
import java.nio.ByteBuffer
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.ConsensualLedgerRequest
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AbstractLedgerExternalEventFactoryTest {
    private val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

    private val abstractLedgerExternalEventFactory = object : AbstractLedgerExternalEventFactory<String>(testClock) {
        override fun createRequest(parameters: String): Any {
            return parameters
        }
    }

    @Test
    fun responseType() {
        assertEquals(EntityResponse::class.java, abstractLedgerExternalEventFactory.responseType)
    }

    @Test
    fun `creates an external event record containing an ConsensualLedgerRequest`() {
        val checkpoint = mock<FlowCheckpoint>()
        val payload = "payload"
        val externalEventContext = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = abstractLedgerExternalEventFactory.createExternalEvent(
            checkpoint,
            externalEventContext,
            payload
        )
        assertEquals(Schemas.VirtualNode.LEDGER_PERSISTENCE_TOPIC, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            ConsensualLedgerRequest(
                testClock.instant(),
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
        val resume = abstractLedgerExternalEventFactory.resumeWith(
            mock(),
            EntityResponse(results)
        )
        assertEquals(results, resume)
    }
}