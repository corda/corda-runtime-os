package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import java.util.concurrent.ConcurrentHashMap

class ClaimStateStoreCacheImpl(
    private val claimStateStoreFactory: ClaimStateStoreFactory,
) : ClaimStateStoreCache {

    private val stateStoreCache = ConcurrentHashMap<TokenPoolKey, ClaimStateStore>()

    override fun get(key: TokenPoolKey): ClaimStateStore {
        return stateStoreCache.compute(key) { k, v ->
            v ?: createClaimStateStore(k)
        }!!
    }

    private fun createClaimStateStore(key: TokenPoolKey): ClaimStateStore {
        return claimStateStoreFactory.create(key)
    }
}
