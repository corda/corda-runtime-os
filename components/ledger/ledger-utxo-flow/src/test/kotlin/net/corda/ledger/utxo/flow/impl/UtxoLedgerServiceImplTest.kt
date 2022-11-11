package net.corda.ledger.utxo.flow.impl

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class UtxoLedgerServiceImplTest: UtxoLedgerTest() {
    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = utxoLedgerService.getTransactionBuilder()
        assertIs<UtxoTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `UtxoLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        val transactionBuilder = utxoLedgerService.getTransactionBuilder()

        val inputStateAndRef = getUtxoInvalidStateAndRef()
        val referenceStateAndRef = getUtxoInvalidStateAndRef()
        val command = UtxoCommandExample()
        val attachment = SecureHash("SHA-256", ByteArray(12))

        val signedTransaction = transactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(inputStateAndRef)
            .addReferenceInputState(referenceStateAndRef)
            .addCommand(command)
            .addAttachment(attachment)
            .sign(publicKeyExample)

        assertIs<UtxoSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)

        val ledgerTransaction = signedTransaction.toLedgerTransaction()

        assertIs<SecureHash>(ledgerTransaction.id)

        Assertions.assertEquals(utxoTimeWindowExample, ledgerTransaction.timeWindow)

        assertIs<List<ContractState>>(ledgerTransaction.outputContractStates)
        Assertions.assertEquals(1, ledgerTransaction.outputContractStates.size)
        Assertions.assertEquals(utxoStateExample, ledgerTransaction.outputContractStates.first())
        assertIs<UtxoStateClassExample>(ledgerTransaction.outputContractStates.first())
    }
}
