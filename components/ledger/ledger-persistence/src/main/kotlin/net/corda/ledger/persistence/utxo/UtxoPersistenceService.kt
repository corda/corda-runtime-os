package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.common.TransactionOutputDto
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState

interface UtxoPersistenceService {
    fun findTransaction(id: String, transactionStatus: TransactionStatus): SignedTransactionContainer?

    fun <T: ContractState> findUnconsumedRelevantStatesByType(stateClass: Class<out T>): List<TransactionOutputDto>

    fun persistTransaction(transaction: UtxoTransactionReader): List<CordaPackageSummary>

    fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)
}
