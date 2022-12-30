package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

/**
 * [UtxoLedgerStateQueryService] allows to find states, [StateRef]s
 */
interface UtxoLedgerStateQueryService {
    /**
     * Find unconsumed relevant states of type [stateClass].
     *
     * @param stateClass The class of the aimed states.
     * @return The result [StateAndRef]s.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun <T: ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>>

    /**
     * Resolve [StateRef]s to [StateAndRef]s
     *
     * @param stateRefs The [StateRef]s to be resolved.
     * @return The resolved [StateAndRef]s.
     *
     * @throws CordaPersistenceException if an error happens during resolve operation.
     */
    @Suspendable
    fun resolveStateRefs(stateRefs: List<StateRef>): List<StateAndRef<*>>
}