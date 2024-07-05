package net.corda.ledger.lib.impl.stub.ledger

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.ledger.lib.common.Constants.CACHE_MAX_SIZE
import net.corda.ledger.utxo.flow.impl.cache.StateAndRefCache
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

class StubStateAndRefCache : StateAndRefCache {
    private val cache: Cache<StateRef, StateAndRef<*>> = CacheFactoryImpl().build(
        "State-And-Ref-Cache",
        Caffeine.newBuilder().maximumSize(CACHE_MAX_SIZE)
    )

    override fun get(stateRefs: Set<StateRef>): Map<StateRef, StateAndRef<*>> {
        return if (stateRefs.isNotEmpty()) {
            cache.getAllPresent(
                stateRefs.map {
                    it
                }
            ).map { (key, value) -> key to value }.toMap()
        } else {
            emptyMap()
        }
    }

    override fun putAll(stateAndRefs: List<StateAndRef<*>>) {
        if (stateAndRefs.isNotEmpty()) {
            cache.putAll(
                stateAndRefs.associateBy { it.ref }
            )
        }
    }
}