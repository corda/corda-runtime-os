package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.transaction.ContractVerificationFailureImpl
import net.corda.metrics.CordaMetrics
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.virtualnode.HoldingIdentity

/**
 * Verifies contracts of ledger transaction. For security reasons, some verifications need to be run with a new instance
 * of transaction.
 *
 * @param transactionFactory factory used for checks that require a new instance of [UtxoLedgerTransaction]
 * @param transaction transaction used for checks that can reuse the same instance of [UtxoLedgerTransaction]
 * @param holdingIdentity the virtual node holding identity to verify against
 * @param injectService: a callback that sets up a contract for testing.
 */
fun verifyContracts(
    transactionFactory: () -> UtxoLedgerTransaction,
    transaction: UtxoLedgerTransaction = transactionFactory.invoke(),
    holdingIdentity: HoldingIdentity,
    injectService: (Contract) -> Unit
) {
    CordaMetrics.Metric.Ledger.ContractVerificationTime
        .builder()
        .forVirtualNode(holdingIdentity.shortHash.toString())
        .build()
        .recordCallable {
            val failureReasons = verifyEncumbrance(transaction).toMutableList()

            val allTransactionStateAndRefs = transaction.inputStateAndRefs + transaction.outputStateAndRefs
            val contractClassMap = allTransactionStateAndRefs.groupBy { it.state.contractType }

            CordaMetrics.Metric.Ledger.ContractVerificationContractCount
                .builder()
                .forVirtualNode(holdingIdentity.shortHash.toString())
                .build()
                .record(contractClassMap.size.toDouble())

            contractClassMap.forEach { (contractClass, contractStates) ->

                CordaMetrics.Metric.Ledger.ContractVerificationContractTime
                    .builder()
                    .forVirtualNode(holdingIdentity.shortHash.toString())
                    .withTag(CordaMetrics.Tag.LedgerContractName, contractClass.name)
                    .build()
                    .recordCallable {
                        try {
                            val contract = contractClass.getConstructor().newInstance()
                            injectService(contract)
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
            }

            if (failureReasons.isNotEmpty()) {
                throw ContractVerificationException(transaction.id, failureReasons)
            }
        }
}
