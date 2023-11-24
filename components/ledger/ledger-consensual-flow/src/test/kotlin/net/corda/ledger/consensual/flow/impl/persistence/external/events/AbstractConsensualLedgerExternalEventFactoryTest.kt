package net.corda.ledger.consensual.flow.impl.persistence.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.persistence.EntityResponse
import net.corda.flow.state.FlowCheckpoint
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

val TEST_CLOCK: Clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
val ALICE_X500_HOLDING_IDENTITY = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group1")

class AbstractConsensualLedgerExternalEventFactoryTest {
    private val abstractConsensualLedgerExternalEventFactory =
        object : AbstractConsensualLedgerExternalEventFactory<String>(TEST_CLOCK) {
            override fun createRequest(parameters: String): Any {
                return parameters
            }
        }

    @Test
    fun responseType() {
        assertEquals(EntityResponse::class.java, abstractConsensualLedgerExternalEventFactory.responseType)
    }

    @Test
    fun `creates an external event record containing an ConsensualLedgerRequest`() {
        val checkpoint = mock<FlowCheckpoint>()
        val payload = "payload"
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            KeyValuePairList(emptyList())
        )

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = abstractConsensualLedgerExternalEventFactory.createExternalEvent(
            checkpoint,
            externalEventContext,
            payload
        )
        assertNull(externalEventRecord.key)
        assertEquals(
            LedgerPersistenceRequest(
                TEST_CLOCK.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                LedgerTypes.CONSENSUAL,
                payload,
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }

    @Test
    fun resumeWith() {
        val results = listOf(ByteBuffer.wrap(byteArrayOf(1, 2, 3)))
        val resume = abstractConsensualLedgerExternalEventFactory.resumeWith(
            mock(),
            EntityResponse(results, KeyValuePairList(emptyList()), null)
        )
        assertEquals(results, resume)
    }
}
