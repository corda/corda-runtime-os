package net.corda.ledger.libs.uniqueness.backingstore

import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

interface BackingStore {
    /**
     * Opens a new session with the backing store and runs the supplied block of code in the
     * context of the session. A session is tied to a specific [holdingIdentity], which must be
     * specified when opening the session.
     */
    fun session(
        holdingIdentity: HoldingIdentity,
        block: (Session) -> Unit
    )

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
            states: Collection<UniquenessCheckStateRef>
        ): Map<UniquenessCheckStateRef, UniquenessCheckStateDetails>

        /**
         * For the given list of transaction id's, returns a map of transaction details for
         * transactions that have been previously committed, keyed by their transaction id.
         */
        fun getTransactionDetails(
            txIds: Collection<SecureHash>
        ): Map<out SecureHash, UniquenessCheckTransactionDetailsInternal>

        /**
         * Provides the set of operations that may be performed within the context of a transaction.
         * To gain access to these operations, call [executeTransaction].
         */
        interface TransactionOps {

            /**
             * Instructs the backing store to record new state records which are marked as
             * unconsumed.
             */
            fun createUnconsumedStates(stateRefs: Collection<UniquenessCheckStateRef>)

            /**
             * Instructs the backing store to mark previously unconsumed states as consumed by
             * the specified transaction id.
             */
            fun consumeStates(
                consumingTxId: SecureHash,
                stateRefs: Collection<UniquenessCheckStateRef>
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
