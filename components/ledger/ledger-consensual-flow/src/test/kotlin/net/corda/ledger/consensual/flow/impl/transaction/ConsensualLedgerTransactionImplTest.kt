package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.consensual.test.ConsensualLedgerTest
import net.corda.ledger.consensual.testkit.ConsensualStateClassExample
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.math.abs
import kotlin.test.assertIs

class ConsensualLedgerTransactionImplTest : ConsensualLedgerTest() {

    @BeforeEach
    fun setup() {
        val flowId = "fc321a0c-62c6-41a1-85e6-e61870ab93aa"
        val suspendCount = 10

        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint

        whenever(checkpoint.flowId).thenReturn(flowId)
        whenever(checkpoint.suspendCount).thenReturn(suspendCount)
    }

    @Test
    fun `ledger transaction contains the same data what it was created with`() {
        val testTimestamp = Instant.now()
        val signedTransaction = ConsensualTransactionBuilderImpl(
            consensualSignedTransactionFactory
        )
            .withStates(consensualStateExample)
            .toSignedTransaction()
        val ledgerTransaction = signedTransaction.toLedgerTransaction()
        assertTrue(abs(ledgerTransaction.timestamp.toEpochMilli() / 1000 - testTimestamp.toEpochMilli() / 1000) < 5)
        assertIs<List<ConsensualState>>(ledgerTransaction.states)
        assertEquals(1, ledgerTransaction.states.size)
        assertEquals(consensualStateExample, ledgerTransaction.states.first())
        assertIs<ConsensualStateClassExample>(ledgerTransaction.states.first())

        assertIs<SecureHash>(ledgerTransaction.id)
    }
}
