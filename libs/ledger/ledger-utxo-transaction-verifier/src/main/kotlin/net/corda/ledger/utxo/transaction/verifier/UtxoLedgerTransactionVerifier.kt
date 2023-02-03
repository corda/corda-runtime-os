package net.corda.ledger.utxo.transaction.verifier

import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class UtxoLedgerTransactionVerifier(private val transaction: UtxoLedgerTransaction): UtxoTransactionVerifier()  {

    override val subjectClass: String = UtxoLedgerTransaction::class.simpleName!!

    fun verify() {
        UtxoTransactionMetadataVerifier(transaction.metadata).verify()
        verifyPlatformChecks()
        UtxoLedgerTransactionContractVerifier(transaction).verify()
    }

    private fun verifyPlatformChecks() {
        /**
         * These checks are shared with [UtxoTransactionBuilderVerifier] verification.
         * They do not require backchain resolution.
         */
        verifySignatories(transaction.signatories)
        verifyInputsAndOutputs(transaction.inputStateRefs, transaction.outputContractStates)
        verifyCommands(transaction.commands)
        verifyNotaryIsWhitelisted()

        /**
         * These checks require backchain resolution.
         */
        verifyInputNotaries()
        verifyInputsAreOlderThanOutputs()
    }


    private fun verifyInputNotaries() {
        val allInputs = transaction.inputTransactionStates + transaction.referenceTransactionStates
        if(allInputs.isEmpty())
            return
        check(allInputs.map { it.notary }.distinct().size == 1) {
            "Input and reference states' notaries need to be the same."
        }
        check(allInputs.first().notary == transaction.notary) {
            "Input and reference states' notaries need to be the same as the $subjectClass's notary."
        }
        // TODO CORE-8958 check rotated notaries
    }

    private fun verifyInputsAreOlderThanOutputs(){
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }

}