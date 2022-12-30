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

    fun resolveStateRefs(stateRefs: List<StateRef>): List<List<ByteArray>>

    fun persistTransaction(transaction: UtxoTransactionReader)

    fun persistTransactionIfDoesNotExist(
        transaction: SignedTransactionContainer,
        transactionStatus: TransactionStatus,
        account: String
    ): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)
}
