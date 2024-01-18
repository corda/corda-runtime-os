package net.corda.ledger.consensual.flow.impl

import net.corda.ledger.consensual.test.ConsensualLedgerTest
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class ConsensualLedgerServiceImplTest : ConsensualLedgerTest() {

    @BeforeEach
    fun setup() {
        val flowId = "fc321a0c-62c6-41a1-85e6-e61870ab93aa"
        val suspendCount = 10

        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint

        whenever(checkpoint.flowId).thenReturn(flowId)
        whenever(checkpoint.suspendCount).thenReturn(suspendCount)
    }

    @Test
    fun `createTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = consensualLedgerService.createTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `ConsensualLedgerServiceImpl's createTransactionBuilder() can build a SignedTransaction`() {
        val transactionBuilder = consensualLedgerService.createTransactionBuilder()
        val signedTransaction = transactionBuilder
            .withStates(consensualStateExample)
            .toSignedTransaction()
        assertIs<ConsensualSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)
    }
}
