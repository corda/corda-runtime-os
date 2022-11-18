package net.corda.ledger.consensual.flow.impl

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.consensual.test.ConsensualLedgerTest
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class ConsensualLedgerServiceImplTest: ConsensualLedgerTest() {
    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = consensualLedgerService.getTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `ConsensualLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        val transactionBuilder = consensualLedgerService.getTransactionBuilder()
        val signedTransaction = transactionBuilder
            .withStates(consensualStateExample)
            .toSignedTransaction(publicKeyExample)
        assertIs<ConsensualSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)
    }
}
