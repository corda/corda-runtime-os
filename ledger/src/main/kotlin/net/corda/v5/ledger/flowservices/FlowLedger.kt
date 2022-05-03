package net.corda.v5.ledger.flowservices

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.StateRef
import net.corda.v5.ledger.transactions.SignedTransaction

interface FlowLedger {

    /**
     * Suspends the flow until the transaction with the specified ID is received, successfully verified and sent to the vault for
     * processing. Note that this call suspends until the transaction is considered valid by the local node, but that doesn't imply the
     * vault will consider it relevant.
     *
     * @param hash The [SecureHash] of the transaction to wait for.
     *
     * @return The [SignedTransaction] that matched the passed in [hash].
     */
    @Suspendable
    fun waitForLedgerCommit(hash: SecureHash): SignedTransaction

    /**
     * Suspends the current flow until all the provided [StateRef]s have been consumed.
     *
     * WARNING! Remember that the flow which uses this async operation will _NOT_ wake-up until all the supplied StateRefs have been
     * consumed. If the node isn't aware of the supplied StateRefs or if the StateRefs are never consumed, then the calling flow will remain
     * suspended FOREVER!!
     *
     * @param stateRefs the StateRefs which will be consumed in the future.
     */
    @Suspendable
    fun waitForStateConsumption(stateRefs: Set<StateRef>)
}
