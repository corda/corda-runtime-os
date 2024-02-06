package net.corda.ledger.persistence.utxo

import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.observer.UtxoToken

interface UtxoPersistenceService {

    /**
     * Find a signed transaction in the persistence context given it's [id].
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
     * Find transactions with the given [transactionIds] that are present in the persistence context and return
     * their IDs and statuses.
     *
     * @param transactionIds IDs of transactions to find.
     *
     * @return A map of the transaction IDs found and their statuses.
     */
    fun findTransactionIdsAndStatuses(transactionIds: List<String>): Map<SecureHash, String>

    /**
     * Find a signed ledger transaction in the persistence context given it's [id] and return it with the status it is stored with. This
     * involves resolving its input and reference state and fetching the transaction's signatures.
     *
     * @param id transaction ID.
     * @param transactionStatus filter for this status.
     *
     * @return The found signed ledger transaction and its status, null if it could not be found in the persistence context.
     *
     * @throws InconsistentLedgerStateException If the ledger tables are inconsistent.
     * @throws CordaRuntimeException If any state refs fail to resolve.
     */
    fun findSignedLedgerTransaction(id: String, transactionStatus: TransactionStatus): Pair<SignedLedgerTransactionContainer?, String?>

    fun <T : ContractState> findUnconsumedVisibleStatesByType(stateClass: Class<out T>): List<UtxoVisibleTransactionOutputDto>

    fun resolveStateRefs(stateRefs: List<StateRef>): List<UtxoVisibleTransactionOutputDto>

    fun persistTransaction(
        transaction: UtxoTransactionReader,
        utxoTokenMap: Map<StateRef, UtxoToken> = emptyMap()
    ): List<CordaPackageSummary>

    fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)

    fun findSignedGroupParameters(hash: String): SignedGroupParameters?

    fun persistSignedGroupParametersIfDoNotExist(signedGroupParameters: SignedGroupParameters)

    /**
     * Persist a list of filtered transactions to the persistence context.
     *
     * @param filteredTransactionsAndSignatures The list of [FilteredTransaction]s to persist and their signature list
     * @param account The account to persist for the [FilteredTransaction]s
     */
    fun persistFilteredTransactions(
        filteredTransactionsAndSignatures: Map<FilteredTransaction, List<DigitalSignatureAndMetadata>>,
        account: String
    )

    fun persistTransactionSignatures(id: String, signatures: List<ByteArray>, startingIndex: Int)

    /**
     * Retrieve filtered transactions and its signatures with the given a list of state references.
     *
     * @param stateRefs The list of [StateRef]
     *
     * @return A map of the filtered transaction ID to a pair of a found [FilteredTransaction] and a corresponding signatures.
     * If a [FilteredTransaction] with a given stateRef ID is not found, it will be null.
     */
    fun findFilteredTransactionsAndSignatures(
        stateRefs: List<StateRef>
    ): Map<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>>
}
