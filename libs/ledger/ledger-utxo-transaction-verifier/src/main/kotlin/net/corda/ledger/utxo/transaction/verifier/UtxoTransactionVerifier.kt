package net.corda.ledger.utxo.transaction.verifier

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import java.security.PublicKey

/*
 * Shared verification for [UtxoTransactionBuilder] and [UtxoLedgerTransaction].
 */
abstract class UtxoTransactionVerifier {
    protected open val subjectClass: String = "transaction"

    protected fun verifySignatories(signatories: List<PublicKey>) {
        check(signatories.isNotEmpty()) {
            "At least one signatory signing key must be applied to the current $subjectClass" +
                " in order to create a signed transaction."
        }
    }

    protected fun verifyInputsAndOutputs(inputStateRefs: List<StateRef>, outputStates: List<*>) {
        check(inputStateRefs.isNotEmpty() || outputStates.isNotEmpty()) {
            "At least one input state, or one output state must be applied to the current $subjectClass."
        }
    }

    protected fun verifyNoDuplicateInputsOrReferences(inputStateRefs: List<StateRef>, referenceStateRefs: List<StateRef>) {
        // The input states part of this check may be repeated later in
        //   net.corda.ledger.utxo.transaction.verifier.UtxoTransactionEncumbranceVerifierKt
        // checkEncumbranceGroup if there is state encumbrance on this transaction.
        val duplicateInputs = inputStateRefs.groupingBy { it }.eachCount().filter { it.value > 1 }

        check(duplicateInputs.isEmpty()) { "Duplicate input states detected: ${duplicateInputs.keys}" }

        val duplicateReferences = referenceStateRefs.groupingBy { it }.eachCount().filter { it.value > 1 }

        check(duplicateReferences.isEmpty()) { "Duplicate reference states detected: ${duplicateReferences.keys}" }
    }

    protected fun verifyNoInputAndReferenceOverlap(inputStateRefs: List<StateRef>, referenceStateRefs: List<StateRef>) {
        val intersection = inputStateRefs intersect referenceStateRefs.toSet()
        check(intersection.isEmpty()) {
            "A state cannot be both an input and a reference input in the same transaction. Offending " +
                "states: $intersection"
        }
    }

    protected fun verifyCommands(commands: List<Command>) {
        check(commands.isNotEmpty()) {
            "At least one command must be applied to the current $subjectClass."
        }
    }
}
