package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import java.security.PublicKey

/*
 * Shared verification for [UtxoTransactionBuilder] and [UtxoLedgerTransaction].
 */
abstract class UtxoTransactionVerifier {
    protected open val subjectClass: String= "transaction"

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

    protected fun verifyCommands(commands: List<Command>) {
        check(commands.isNotEmpty()) {
            "At least one command must be applied to the current $subjectClass."
        }
    }

    protected fun verifyNotaryIsWhitelisted() {
        // TODO Check the notary is in the group parameters whitelist
    }
}