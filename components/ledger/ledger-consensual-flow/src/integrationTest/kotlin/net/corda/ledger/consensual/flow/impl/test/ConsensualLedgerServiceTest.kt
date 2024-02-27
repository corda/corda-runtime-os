package net.corda.ledger.consensual.flow.impl.test

import net.corda.ledger.consensual.testkit.ConsensualLedgerIntegrationTest
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConsensualLedgerServiceTest : ConsensualLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `createTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = consensualLedgerService.createTransactionBuilder()
        assertThat(transactionBuilder).isInstanceOf(ConsensualTransactionBuilder::class.java)
    }
}
