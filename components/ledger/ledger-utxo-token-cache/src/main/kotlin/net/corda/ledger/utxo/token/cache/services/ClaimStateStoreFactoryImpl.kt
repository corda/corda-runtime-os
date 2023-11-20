package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.time.Clock

class ClaimStateStoreFactoryImpl(
    private val stateManager: StateManager,
    private val serialization: TokenPoolCacheStateSerialization,
    private val tokenPoolCacheManager: TokenPoolCacheManager,
    private val clock: Clock
) : ClaimStateStoreFactory {

    override fun create(key: TokenPoolKey): ClaimStateStore {
        return PerformanceClaimStateStoreImpl(
            key,
            serialization,
            stateManager,
            tokenPoolCacheManager,
            clock
        )
    }
}
