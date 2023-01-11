package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.ContractVerificationFailure
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class UtxoLedgerTransactionContractVerifier(private val transaction: UtxoLedgerTransaction) {
    private val failureReasons = mutableListOf<ContractVerificationFailure>()

    fun verify() {
        failureReasons.addAll(verifyEncumberedInput(transaction.inputStateAndRefs))

        val allTransactionStateAndRefs = transaction.inputStateAndRefs + transaction.outputStateAndRefs
        val contractClassMap = allTransactionStateAndRefs.groupBy { it.state.contractType }

        contractClassMap.forEach { (contractClass, contractStates) ->
            try {
                val contract = contractClass.getConstructor().newInstance()
                contract.verify(transaction)
            } catch (ex: Exception) {
                failureReasons.add(
                    ContractVerificationFailureImpl(
                        contractClassName = contractClass.canonicalName,
                        contractStateClassNames = contractStates.map { it.state.contractState.javaClass.canonicalName },
                        exceptionClassName = ex.javaClass.canonicalName,
                        exceptionMessage = ex.message ?: "The thrown exception did not provide a failure message."
                    )
                )
            }
        }

        if (failureReasons.isNotEmpty()) {
            throw ContractVerificationException(transaction.id, failureReasons)
        }
    }
}