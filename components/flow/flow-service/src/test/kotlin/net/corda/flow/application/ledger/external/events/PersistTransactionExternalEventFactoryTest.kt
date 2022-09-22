package net.corda.flow.application.ledger.external.events

import net.corda.data.KeyValuePairList
import java.nio.ByteBuffer
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.ConsensualLedgerRequest
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

class PersistTransactionExternalEventFactoryTest {

    @Test
    fun `creates a record containing an ConsensualLedgerRequest with a PersistTransaction payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))
        val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val transaction = ByteBuffer.wrap(byteArrayOf(1))

        val externalEventRecord = PersistTransactionExternalEventFactory(testClock).createExternalEvent(
            checkpoint,
            externalEventContext,
            PersistTransactionParameters(transaction)
        )
        assertEquals(Schemas.VirtualNode.LEDGER_PERSISTENCE_TOPIC, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            ConsensualLedgerRequest(
                testClock.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                PersistTransaction(transaction),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}