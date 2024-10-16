package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.v5.ledger.utxo.Contract
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
    private val transaction: UtxoLedgerTransaction = transactionFactory.invoke(),
    private val metricFactory: ContractVerificationMetricFactory,
    private val injectService: (Contract) -> Unit
) : UtxoTransactionVerifier() {

    override val subjectClass: String = UtxoLedgerTransaction::class.simpleName!!

    fun verify() {
        verifyMetadata(transaction.metadata) // cheap check this transaction is UTXO
        verifyPlatformChecks() // cheap local checks about state overlaps and notaries

        // Now do the much more expensive sandbox checks requiring sandboxes
        verifyContracts(transactionFactory, transaction, injectService, metricFactory)
    }

    private fun verifyPlatformChecks() {
        /**
         * These checks are shared with [UtxoTransactionBuilderVerifier] verification.
         * They do not require backchain resolution; they are all surface level, very fast checks.
         */
        verifySignatories(transaction.signatories)
        verifyInputsAndOutputs(transaction.inputStateRefs, transaction.outputContractStates)
        verifyNoDuplicateInputsOrReferences(transaction.inputStateRefs, transaction.referenceStateRefs)
        verifyNoInputAndReferenceOverlap(transaction.inputStateRefs, transaction.referenceStateRefs)
        verifyCommands(transaction.commands)

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
        check(allInputs.map { it.notaryName }.distinct().size == 1) {
            "Input and reference states' notaries need to be the same."
        }
        check(allInputs.first().notaryName == transaction.notaryName) {
            "Input and reference states' notaries need to be the same as the $subjectClass's notary."
        }
    }

    private fun verifyInputsAreOlderThanOutputs() {
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }
}
