package net.corda.ledger.utxo.flow.impl.test

import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtxoLedgerServiceTest: UtxoLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `createTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = utxoLedgerService.createTransactionBuilder()
        assertThat(transactionBuilder).isInstanceOf(UtxoTransactionBuilder::class.java)
    }
}