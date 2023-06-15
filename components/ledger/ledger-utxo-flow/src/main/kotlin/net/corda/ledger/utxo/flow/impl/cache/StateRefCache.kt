package net.corda.ledger.utxo.flow.impl.cache

import net.corda.ledger.utxo.data.state.LazyStateAndRefImpl
import net.corda.v5.ledger.utxo.StateRef

interface StateRefCache {
    fun get(stateRefs: Set<StateRef>): Map<StateRef, LazyStateAndRefImpl<*>?>

    fun putAll(stateRefs: Map<StateRef, LazyStateAndRefImpl<*>?>)
}
