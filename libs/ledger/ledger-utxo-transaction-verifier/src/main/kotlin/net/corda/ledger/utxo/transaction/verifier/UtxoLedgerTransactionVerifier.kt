package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/**
 * Verifies ledger transaction. For security reasons, some verifications (e.g. contracts) need to be run with a new
 * instance of transaction.
 *
 * @param transactionFactory factory used for checks that require a new instance of [UtxoLedgerTransaction]
 * @param transaction transaction used for checks that can reuse the same instance of [UtxoLedgerTransaction]
 */
class UtxoLedgerTransactionVerifier(
    private val transactionFactory: () -> UtxoLedgerTransaction,
    private val transaction: UtxoLedgerTransaction = transactionFactory.invoke()
) : UtxoTransactionVerifier() {

    override val subjectClass: String = UtxoLedgerTransaction::class.simpleName!!

    fun verify() {
        verifyMetadata(transaction.metadata)
        verifyPlatformChecks()
        verifyContracts(transactionFactory, transaction)
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
        if (allInputs.isEmpty()) {
            return
        }
        check(allInputs.map { it.notary }.distinct().size == 1) {
            "Input and reference states' notaries need to be the same."
        }
        check(allInputs.first().notary == transaction.notary) {
            "Input and reference states' notaries need to be the same as the $subjectClass's notary."
        }
        // TODO CORE-8958 check rotated notaries
    }

    private fun verifyInputsAreOlderThanOutputs() {
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }

}