package net.corda.uniqueness.backingstore

import net.corda.lifecycle.Lifecycle
import net.corda.uniqueness.backingstore.BackingStore.Session
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails
import net.corda.virtualnode.HoldingIdentity

/**
 * Abstracts the retrieval and persistence of data required by uniqueness checker implementations.
 * Data supplied to and returned by the backing store use a suite of `UniquenessCheckInternal`
 * classes, which are agnostic to both the external messaging API used by uniqueness checker
 * implementations and any underlying database schema / data structures used by backing store
 * implementations.
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

interface BackingStore : Lifecycle {

    /**
     * Opens a new session with the backing store and runs the supplied block of code in the
     * context of the session. A session is tied to a specific [holdingIdentity], which must be
     * specified when opening the session.
     */
    fun session(holdingIdentity: HoldingIdentity, block: (Session) -> Unit)

    /**
     * Convenience function which opens a session with the backing store and then immediately
     * begins executing a transaction, allowing supplied code immediate access to both [Session]
     * and [TransactionOps][Session.TransactionOps] interfaces. Useful when you know you will need
     * to perform commit operations up front.
     */
    fun transactionSession(
        holdingIdentity: HoldingIdentity,
        block: (Session, Session.TransactionOps) -> Unit
    ) {
        session(holdingIdentity) { session -> session.executeTransaction(block) }
    }

    /**
     * Provides the set of operations that may be performed within the context of a session with.
     * the backing store. To gain access to these operations, call [session].
     */
    interface Session {

        /**
         * Executes a transaction within the scope of the specified code block. The [TransactionOps]
         * interface will be injected into the code block, which may perform one or more operations
         * using the interface. Once the specified code block has been executed, any changes to
         * the data persisted data are committed atomically.
         */
        fun executeTransaction(block: (Session, TransactionOps) -> Unit)

        /**
         * For the given list of state references, returns a map of state details for states that
         * have been previously committed, keyed by their state reference.
         */
        fun getStateDetails(
            states: Collection<StateRef>
        ): Map<StateRef, UniquenessCheckStateDetails>

        /**
         * For the given list of transaction id's, returns a map of transaction details for
         * transactions that have been previously committed, keyed by their transaction id.
         */
        fun getTransactionDetails(
            txIds: Collection<SecureHash>
        ): Map<SecureHash, UniquenessCheckTransactionDetailsInternal>

        /**
         * Provides the set of operations that may be performed within the context of a transaction.
         * To gain access to these operations, call [executeTransaction].
         */
        interface TransactionOps {

            /**
             * Instructs the backing store to record new state records which are marked as
             * unconsumed.
             */
            fun createUnconsumedStates(stateRefs: Collection<StateRef>)

            /**
             * Instructs the backing store to mark previously unconsumed states as consumed by
             * the specified transaction id.
             */
            fun consumeStates(
                consumingTxId: SecureHash,
                stateRefs: Collection<StateRef>
            )

            /**
             * Instructs the backing store to commit the details of the specified transactions.
             */
            fun commitTransactions(
                transactionDetails: Collection<Pair<
                        UniquenessCheckRequestInternal, UniquenessCheckResult>>
            )
        }
    }
}
