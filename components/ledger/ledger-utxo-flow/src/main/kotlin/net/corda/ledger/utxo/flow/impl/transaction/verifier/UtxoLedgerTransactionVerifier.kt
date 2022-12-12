package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class UtxoLedgerTransactionVerifier(private val ledgerTransaction: UtxoLedgerTransaction): UtxoTransactionVerifier()  {

    fun verifyPlatformChecks(notary: Party) {
        /**
         * These checks are shared with [UtxoTransactionBuilderVerifier] verification.
         * They do not require backchain resolution.
         */
        verifySignatories(ledgerTransaction.signatories)
        verifyInputsAndOutputs(ledgerTransaction.inputStateRefs, ledgerTransaction.outputContractStates)
        verifyCommands(ledgerTransaction.commands)
        verifyNotaryIsWhitelisted()

        /**
         * These checks require backchain resolution.
         */
        verifyInputNotaries(notary)
        verifyInputsAreOlderThanOutputs()
    }

    fun verifyContracts() {
        // TODO : In another PR...
    }


    private fun verifyInputNotaries(notary: Party) {
        val allInputs = ledgerTransaction.inputTransactionStates + ledgerTransaction.referenceInputTransactionStates
        if(allInputs.isEmpty())
            return
        check(allInputs.map { it.notary }.distinct().size == 1) {
            "Input and Reference input states' notaries need to be the same. ${allInputs.map { it.notary }.distinct().size}"
        }
        check(allInputs.first().notary == notary) {
            "Input and Reference input states' notaries need to be the same as the transaction's notary."
        }
        // TODO check rotated notaries
    }

    private fun verifyInputsAreOlderThanOutputs(){
        // TODO needs to access the previous transactions from the backchain somehow
    }

}