package net.corda.ledger.consensual.flow.impl

import net.corda.ledger.consensual.test.ConsensualLedgerTest
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class ConsensualLedgerServiceImplTest : ConsensualLedgerTest() {
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
