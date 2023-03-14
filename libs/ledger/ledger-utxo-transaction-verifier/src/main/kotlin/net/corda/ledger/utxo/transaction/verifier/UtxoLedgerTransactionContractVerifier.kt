package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.transaction.ContractVerificationFailureImpl
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/**
 * Verifies contracts of ledger transaction. For security reasons, some verifications need to be run with a new instance
 * of transaction.
 *
 * @param transactionFactory factory used for checks that require a new instance of [UtxoLedgerTransaction]
 * @param transaction transaction used for checks that can reuse the same instance of [UtxoLedgerTransaction]
 */
fun verifyContracts(
    transactionFactory: () -> UtxoLedgerTransaction,
    transaction: UtxoLedgerTransaction = transactionFactory.invoke()
) {
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

fun verifyMetadata(metadata: TransactionMetadata) {
    println(metadata)
}
