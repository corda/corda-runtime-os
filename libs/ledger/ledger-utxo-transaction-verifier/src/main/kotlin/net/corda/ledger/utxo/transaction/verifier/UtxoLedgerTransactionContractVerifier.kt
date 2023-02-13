package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.transaction.ContractVerificationFailureImpl
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

fun verifyContracts(transactionFactory: () -> UtxoLedgerTransaction) {
    val transaction = transactionFactory.invoke()
    val failureReasons = verifyEncumbrance(transaction).toMutableList()

    val allTransactionStateAndRefs = transaction.inputStateAndRefs + transaction.outputStateAndRefs
    val contractClassMap = allTransactionStateAndRefs.groupBy { it.state.contractType }

    contractClassMap.forEach { (contractClass, contractStates) ->
        try {
            val contract = contractClass.getConstructor().newInstance()
            contract.verify(transactionFactory.invoke())
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