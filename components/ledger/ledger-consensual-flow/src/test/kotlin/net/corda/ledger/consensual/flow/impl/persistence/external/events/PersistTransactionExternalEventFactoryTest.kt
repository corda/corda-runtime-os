package net.corda.ledger.consensual.flow.impl.persistence.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.common.data.transaction.TransactionStatus
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

class PersistTransactionExternalEventFactoryTest {

    @Test
    fun `creates a record containing an ConsensualLedgerRequest with a PersistTransaction payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            KeyValuePairList(emptyList())
        )
        val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val transaction = ByteBuffer.wrap(byteArrayOf(1))
        val transactionStatus = TransactionStatus.VERIFIED.value

        val externalEventRecord = PersistTransactionExternalEventFactory(testClock).createExternalEvent(
            checkpoint,
            externalEventContext,
            PersistTransactionParameters(transaction, transactionStatus)
        )
        assertEquals(Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            LedgerPersistenceRequest(
                testClock.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                LedgerTypes.CONSENSUAL,
                PersistTransaction(transaction, transactionStatus, emptyList()),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}