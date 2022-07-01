package net.corda.uniqueness.backingstore

import net.corda.data.uniqueness.UniquenessCheckRequest
import net.corda.uniqueness.datamodel.UniquenessCheckInternalResult
import net.corda.uniqueness.datamodel.UniquenessCheckInternalStateDetails
import net.corda.uniqueness.datamodel.UniquenessCheckInternalTransactionDetails
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.StateRef

/**
 * Provides an interface to the backing database responsible for storing data used by notary
 * services.
 *
 * A number of functional operations are provided by the backing store. These can be categorised
 * as transactional or non-transactional, which is generally determined by whether operations are
 * committing data to the backing store, or retrieving data.
 *
 * All operations must be run within the context of a session with the backing store, irrespective
 * of the type of operation. Multiple operations may be performed within one session. A session is
 * started by calling [session], which takes a block of code to execute within the context of the
 * session. The [Session] interface is injected into the block of code being executed, which
 * provides methods for retrieving data from the backing store.
 *
 * The [Session] interface also provides an [executeTransaction][Session.executeTransaction] method,
 * which behaves in much the same way as [session], taking a block of code to run within the context
 * of a transaction. This block of code is provided with both the [Session] interface, but also a
 * [TransactionOps][Session.TransactionOps] interface which can be used to invoke operations that
 * involve committing data to the backing store. Any data that is written as a result of operations
 * invoked within this code block will be committed atomically.
 *
 * Example usage:
 *
 * ```
 * backingStore.session { session ->
 *     // Call methods from the Session interface to retrieve data
 *     session.executeTransaction() { session, txnOps ->
 *         // Call methods from the TransactionOps interface to commit data
 *     }
 * }
 * ```
 */
@Suppress("ForbiddenComment")
interface BackingStore {

    /**
     * Opens a new session with the backing store and runs the supplied block of code in the
     * context of the session.
     */
    fun session(block: (Session) -> Unit)

    /**
     * Convenience function which opens a session with the backing store and then immediately
     * begins executing a transaction, allowing supplied code immediate access to both [Session]
     * and [TransactionOps][Session.TransactionOps] interfaces. Useful when you know you will need
     * to perform commit operations up front.
     */
    fun transactionSession(block: (Session, Session.TransactionOps) -> Unit) {
        session { session -> session.executeTransaction(block) }
    }

    /**
     * Ends this instance of the backing store. Should be called when the backing store is
     * no longer required.
     */
    fun close()

    /**
     * Provides the set of operations that may be performed within the context of a session with.
     * the backing store. To gain access to these operations, call [session].
     */
    interface Session {

        /**
         * Executes a transaction within the scope of the specified code block. The [TransactionOps]
         * interface will be injected into the code block, which may perform one or more operations
         * using the interface. All operations performed that result in writes to the backing store
         * will be performed atomically.
         */
        fun executeTransaction(block: (Session, TransactionOps) -> Unit)

        /**
         * For the given list of state refs, returns a list of committed state objects for states
         * that have been previously committed (spent).
         */
        fun getCommittedStates(states: Collection<StateRef>): List<UniquenessCheckInternalStateDetails>

        /**
         * For the given list of transaction id's, returns a list of those ids which have been
         * previously committed.
         */
        fun getCommittedTransactions(txIds: Collection<SecureHash>): List<UniquenessCheckInternalTransactionDetails>

        /**
         * For the given list of transaction id's, returns a list of those ids which have been
         * previously rejected.
         */
        fun getRejectedTransactions(txIds: Collection<SecureHash>): List<UniquenessCheckInternalTransactionDetails>

        /**
         * Returns whether a transaction with the specified hash has previously been committed.
         */
        fun isPreviouslyNotarised(txId: SecureHash): Boolean

        /**
         * Provides the set of operations that may be performed within the context of a transaction.
         * To gain access to these operations, call [executeTransaction].
         */
        interface TransactionOps {

            /**
             * This function will update the given state records in the backing store and set the
             * consuming transaction to the given transaction.
             */
            fun consumeStates(consumingTxIdsAndStateRefs: Pair<SecureHash, List<StateRef>>)

            /**
             * This function will create new state records in the backing store.
             * These state records will be *unconsumed* and the issuer will be the given
             * transaction.
             */
            fun createUnconsumedStates(issueTxIdAndStateRefs: Pair<SecureHash, List<StateRef>>)

            /**
             * This function will commit the transactions from the given requests to the backing store.
             * The logic of how it is committed will depend on the result.
             */
            fun commitTransactions(
                transactionResults: List<Pair<UniquenessCheckRequest, UniquenessCheckInternalResult>>
            )
        }
    }
}
