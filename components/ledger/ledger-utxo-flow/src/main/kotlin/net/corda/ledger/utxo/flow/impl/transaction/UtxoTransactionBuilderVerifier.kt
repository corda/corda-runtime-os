package net.corda.ledger.utxo.flow.impl.transaction

class UtxoTransactionBuilderVerifier(private val transactionBuilder: UtxoTransactionBuilderImpl) {

    fun verify() {
        verifyNotary()
        verifySignatories()
        verifyTimeWindow()
        verifyInputsAndOutputs()
        verifyCommands()
        verifyEncumbranceGroups()
        // TODO : Contract Verification
    }

    private fun verifyNotary() {
        checkNotNull(transactionBuilder.notary) {
            "The notary of the current transaction builder must not be null."
        }
    }

    private fun verifySignatories() {
        check(transactionBuilder.signatories.isNotEmpty()) {
            "At least one signatory signing key must be applied to the current transaction builder in order to create a signed transaction."
        }
    }

    private fun verifyTimeWindow() {
        checkNotNull(transactionBuilder.timeWindow) {
            "The time window of the current transaction builder must not be null."
        }
    }

    private fun verifyInputsAndOutputs() {
        check(transactionBuilder.inputStateAndRefs.isNotEmpty() || transactionBuilder.outputStates.isNotEmpty()) {
            "At least one input state, or one output state must be applied to the current transaction builder."
        }
    }

    private fun verifyCommands() {
        check(transactionBuilder.commands.isNotEmpty()) {
            "At least one command must be applied to the current transaction builder."
        }
    }

    private fun verifyEncumbranceGroups() {
        check(transactionBuilder.getEncumbranceGroups().all { it.value.size > 1 }) {
            "Every encumbrance group of the current transaction builder must contain more than one output state."
        }
    }
}