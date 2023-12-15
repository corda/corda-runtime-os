package net.corda.ledger.consensual.flow.impl.persistence.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class FindTransactionExternalEventFactoryTest {

    @Test
    fun `creates a record containing an ConsensualLedgerRequest with a FindTransaction payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            KeyValuePairList(emptyList())
        )
        val transactionId = "1234567890123456"
        val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = FindTransactionExternalEventFactory(testClock).createExternalEvent(
            checkpoint,
            externalEventContext,
            FindTransactionParameters(transactionId)
        )

        assertNull(externalEventRecord.key)
        assertEquals(
            LedgerPersistenceRequest(
                testClock.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                LedgerTypes.CONSENSUAL,
                FindTransaction(transactionId, TransactionStatus.VERIFIED.value),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}
