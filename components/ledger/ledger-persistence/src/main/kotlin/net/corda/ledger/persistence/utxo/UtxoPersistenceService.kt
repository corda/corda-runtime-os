package net.corda.ledger.persistence.utxo

import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef

interface UtxoPersistenceService {
    fun findTransaction(id: String, transactionStatus: TransactionStatus): Pair<SignedTransactionContainer?, String?>

    fun <T: ContractState> findUnconsumedVisibleStatesByType(stateClass: Class<out T>): List<UtxoTransactionOutputDto>

    fun resolveStateRefs(stateRefs: List<StateRef>): List<UtxoTransactionOutputDto>

    fun persistTransaction(transaction: UtxoTransactionReader): List<CordaPackageSummary>

    fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)

    fun findSignedGroupParameters(hash: String): SignedGroupParameters?

    fun persistSignedGroupParametersIfDoNotExist(signedGroupParameters: SignedGroupParameters)
}
