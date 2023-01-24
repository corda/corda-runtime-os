package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.data.transaction.ContractVerificationFailureImpl
import net.corda.ledger.utxo.data.transaction.ContractVerificationResult
import net.corda.ledger.utxo.data.transaction.ContractVerificationStatus
import net.corda.v5.ledger.utxo.ContractVerificationFailure
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

fun verifyTransactionContracts(transaction: UtxoLedgerTransaction): ContractVerificationResult {
    val failureReasons = mutableListOf<ContractVerificationFailure>()
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

    val status =
        if (failureReasons.isEmpty()) ContractVerificationStatus.VERIFIED
        else ContractVerificationStatus.INVALID

    return ContractVerificationResult(status, failureReasons)
}
