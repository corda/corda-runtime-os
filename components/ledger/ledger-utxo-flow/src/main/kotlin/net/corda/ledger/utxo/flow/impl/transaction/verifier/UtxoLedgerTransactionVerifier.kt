package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class UtxoLedgerTransactionVerifier(private val ledgerTransaction: UtxoLedgerTransaction) {

    fun verifyPlatformChecks(notary: Party) {
        verifyInputNotaries(notary)
        verifyInputsAreOlderThanOutputs()
    }

    fun verifyContracts() {
        // TODO : In another PR...
    }

    // TODO check rotated notaries
    private fun verifyInputNotaries(notary: Party) {
        val allInputs = ledgerTransaction.inputTransactionStates + ledgerTransaction.referenceInputTransactionStates
        check(allInputs.map { it.notary}.size == 1) {
            "Input and Reference input states' notaries needs to be the same."
        }
        check(allInputs.first().notary == notary) {
            "Input and Reference input states' notaries needs to be the same as the transaction's notary."
        }
    }

    private fun verifyInputsAreOlderThanOutputs(){
        // TODO needs to access the previous transactions from the backchain somehow
    }

}