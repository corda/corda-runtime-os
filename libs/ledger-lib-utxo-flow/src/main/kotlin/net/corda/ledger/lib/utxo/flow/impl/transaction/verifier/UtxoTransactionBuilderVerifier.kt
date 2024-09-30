package net.corda.ledger.lib.utxo.flow.impl.transaction.verifier

import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.transaction.verifier.UtxoTransactionVerifier
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

/**
 * Utxo Transaction verification checks which do not require resolved states.
 * [UtxoTransactionVerifier] contains the ones shared with ledger transaction verifications.
 * The ones in this file are pointless on Ledger Transactions.
 */
class UtxoTransactionBuilderVerifier(
    private val transactionBuilder: UtxoTransactionBuilderInternal
) :
    UtxoTransactionVerifier() {
    override val subjectClass: String = UtxoTransactionBuilder::class.simpleName!!

    fun verify() {
        /**
         * These checks are unique to [UtxoTransactionBuilder].
         * The related fields are not nullable or do not exist in [UtxoLedgerTransaction].
         */
        verifyNotary()
        verifyTimeWindow()
        verifyEncumbranceGroups()

        /**
         * These checks are shared with [UtxoLedgerTransactionVerifier] verification.
         */
        verifySignatories(transactionBuilder.signatories)
        verifyInputsAndOutputs(transactionBuilder.inputStateRefs, transactionBuilder.outputStates)
        verifyNoDuplicateInputsOrReferences(transactionBuilder.inputStateRefs, transactionBuilder.referenceStateRefs)
        verifyNoInputAndReferenceOverlap(transactionBuilder.inputStateRefs, transactionBuilder.referenceStateRefs)
        verifyCommands(transactionBuilder.commands)
    }

    private fun verifyNotary() {
        checkNotNull(transactionBuilder.notaryName) {
            "The notary name of the current $subjectClass must not be null."
        }
        checkNotNull(transactionBuilder.notaryKey) {
            "The notary ${transactionBuilder.notaryName} of the current $subjectClass cannot be found or is not a valid notary."
        }
    }

    private fun verifyTimeWindow() {
        checkNotNull(transactionBuilder.timeWindow) {
            "The time window of the current $subjectClass must not be null."
        }
    }

    private fun verifyEncumbranceGroups() {
        check(transactionBuilder.encumbranceGroups.all { it.value.size > 1 }) {
            "Every encumbrance group of the current $subjectClass must contain more than one output state."
        }
    }
}
