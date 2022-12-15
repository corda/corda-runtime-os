package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef

interface UtxoPersistenceService {
    fun findTransaction(id: String, transactionStatus: TransactionStatus): SignedTransactionContainer?

    fun <T: ContractState> findUnconsumedRelevantStatesByType(id: String, stateClass: Class<out T>): List<StateAndRef<T>>

    fun persistTransaction(transaction: UtxoTransactionReader)

    fun persistTransactionIfDoesNotExist(
        transaction: SignedTransactionContainer,
        transactionStatus: TransactionStatus,
        account: String
    ): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)
}
