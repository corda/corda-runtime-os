package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

// TODO Move this class to VerificationProcessor and replace it here with UtxoLedgerTransactionVerifierComponent (CORE-9385)
class UtxoLedgerTransactionVerifier(private val transaction: UtxoLedgerTransaction): UtxoTransactionVerifier()  {

    override val subjectClass: String = UtxoLedgerTransaction::class.simpleName!!

    fun verify(notary: Party) {
        verifyPlatformChecks(notary)
        UtxoLedgerTransactionContractVerifier(transaction).verify()
    }

    private fun verifyPlatformChecks(notary: Party) {
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
        verifyInputNotaries(notary)
        verifyInputsAreOlderThanOutputs()
    }


    private fun verifyInputNotaries(notary: Party) {
        val allInputs = transaction.inputTransactionStates + transaction.referenceTransactionStates
        if(allInputs.isEmpty())
            return
        check(allInputs.map { it.notary }.distinct().size == 1) {
            "Input and reference states' notaries need to be the same."
        }
        check(allInputs.first().notary == notary) {
            "Input and reference states' notaries need to be the same as the $subjectClass's notary."
        }
        // TODO CORE-8958 check rotated notaries
    }

    private fun verifyInputsAreOlderThanOutputs(){
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }

}