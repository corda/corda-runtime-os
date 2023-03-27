package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.persistence.EntityResponse
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
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

class AbstractUtxoLedgerExternalEventFactoryTest {
    private val abstractUtxoLedgerExternalEventFactory =
        object : AbstractUtxoLedgerExternalEventFactory<String>(TEST_CLOCK) {
            override fun createRequest(parameters: String): Any {
                return parameters
            }
        }

    @Test
    fun responseType() {
        assertEquals(EntityResponse::class.java, abstractUtxoLedgerExternalEventFactory.responseType)
    }

    @Test
    fun `creates an external event record containing an UtxoLedgerRequest`() {
        val checkpoint = mock<FlowCheckpoint>()
        val payload = "payload"
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            KeyValuePairList(emptyList())
        )

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = abstractUtxoLedgerExternalEventFactory.createExternalEvent(
            checkpoint,
            externalEventContext,
            payload
        )
        assertEquals(Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            LedgerPersistenceRequest(
                TEST_CLOCK.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                LedgerTypes.UTXO,
                payload,
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }

    @Test
    fun resumeWith() {
        val results = listOf(ByteBuffer.wrap(byteArrayOf(1, 2, 3)))
        val resume = abstractUtxoLedgerExternalEventFactory.resumeWith(
            mock(),
            EntityResponse(results)
        )
        assertEquals(results, resume)
    }
}