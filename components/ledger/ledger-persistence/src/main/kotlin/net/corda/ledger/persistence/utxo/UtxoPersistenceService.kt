package net.corda.ledger.persistence.utxo

import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.utxo.data.transaction.LedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.observer.UtxoToken

interface UtxoPersistenceService {

    /**
     * Find a verified signed transaction in the persistence context given it's [id].
     *
     * @param id transaction ID.
     * @param transactionStatus filter for this status.
     *
     * @return The found signed transaction and its status, null if it could not be found in the persistence context.
     *
     * @throws InconsistentLedgerStateException If the ledger tables are inconsistent.
     */
    fun findSignedTransaction(id: String, transactionStatus: TransactionStatus): Pair<SignedTransactionContainer?, String?>

    /**
     * Find a verified signed ledger transaction in the persistence context given it's [id] and return it with the status it is stored with.
     * This involves resolving its input and reference state and fetching the transaction's signatures.
     *
     * @param id transaction ID.
     * @param transactionStatus filter for this status.
     *
     * @return The found signed ledger transaction and its status, null if it could not be found in the persistence context.
     *
     * @throws InconsistentLedgerStateException If the ledger tables are inconsistent.
     * @throws CordaRuntimeException If any state refs fail to resolve.
     */
    fun findSignedLedgerTransaction(id: String, transactionStatus: TransactionStatus): Pair<LedgerTransactionContainer?, String?>

    fun <T: ContractState> findUnconsumedVisibleStatesByType(stateClass: Class<out T>): List<UtxoTransactionOutputDto>

    fun resolveStateRefs(stateRefs: List<StateRef>): List<UtxoTransactionOutputDto>

    fun persistTransaction(
        transaction: UtxoTransactionReader,
        utxoTokenMap: Map<StateRef, UtxoToken> = emptyMap()
    ): List<CordaPackageSummary>

    fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)

    fun findSignedGroupParameters(hash: String): SignedGroupParameters?

    fun persistSignedGroupParametersIfDoNotExist(signedGroupParameters: SignedGroupParameters)
}
