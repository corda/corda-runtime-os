package net.corda.ledger.libs.verification.impl

import net.corda.ledger.libs.verification.UtxoTransactionVerifier
import net.corda.ledger.libs.verification.impl.VerificationUtils.verifyCommands
import net.corda.ledger.libs.verification.impl.VerificationUtils.verifyInputsAndOutputs
import net.corda.ledger.libs.verification.impl.VerificationUtils.verifyNoDuplicateInputsOrReferences
import net.corda.ledger.libs.verification.impl.VerificationUtils.verifyNoInputAndReferenceOverlap
import net.corda.ledger.libs.verification.impl.VerificationUtils.verifySignatories
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class UtxoLedgerUtxoTransactionVerifierImpl(
    private val transactionFactory: () -> UtxoLedgerTransaction,
    private val transaction: UtxoLedgerTransaction = transactionFactory.invoke(),
    private val verify: (UtxoLedgerTransaction) -> Unit,
): UtxoTransactionVerifier {

    override fun verify() {
        verifySignatories(transaction.signatories)
        verifyInputsAndOutputs(transaction.inputStateRefs, transaction.outputContractStates)
        verifyNoDuplicateInputsOrReferences(transaction.inputStateRefs, transaction.referenceStateRefs)
        verifyNoInputAndReferenceOverlap(transaction.inputStateRefs, transaction.referenceStateRefs)
        verifyCommands(transaction.commands)

        verifyInputNotaries()
        verifyInputsAreOlderThanOutputs()

        verify(transaction)
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
            "Input and reference states' notaries need to be the same as the transaction's notary."
        }
    }

    private fun verifyInputsAreOlderThanOutputs() {
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }
}
