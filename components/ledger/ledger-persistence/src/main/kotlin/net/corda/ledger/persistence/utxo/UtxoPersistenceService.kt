package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.utxo.impl.UtxoTransactionOutputDto
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef

interface UtxoPersistenceService {
    fun findTransaction(id: String, transactionStatus: TransactionStatus): SignedTransactionContainer?

    fun <T: ContractState> findUnconsumedRelevantStatesByType(stateClass: Class<out T>): List<UtxoTransactionOutputDto>

    fun resolveStateRefs(stateRefs: List<StateRef>): List<UtxoTransactionOutputDto>

    fun persistTransaction(transaction: UtxoTransactionReader): List<CordaPackageSummary>

    fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)
}
