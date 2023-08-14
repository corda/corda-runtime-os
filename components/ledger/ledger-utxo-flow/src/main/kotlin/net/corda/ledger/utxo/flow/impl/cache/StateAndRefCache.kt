package net.corda.ledger.utxo.flow.impl.cache

import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

/**
 * A cache for storing [StateAndRef]s keyed on [StateRef]s.
 */
interface StateAndRefCache {

    /**
     * Returns a map of [StateRef] and [StateAndRef] associations from the cache for the given [stateRefs].
     * Please note that this function WON'T return `null`s. This means, if a there's no [StateAndRef]
     * object for a given [StateRef], it will not be present in the returned map.
     */
    fun get(stateRefs: Set<StateRef>): Map<StateRef, StateAndRef<*>>

    /**
     * Inserts the provided [stateAndRefs] list to the cache. It will be keyed on the [StateRef].
     */
    fun putAll(stateAndRefs: List<StateAndRef<*>>)
}
