package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.time.Clock

class ClaimStateStoreFactoryImpl(
    private val stateManager: StateManager,
    private val serialization: TokenPoolCacheStateSerialization,
    private val tokenPoolCacheManager: TokenPoolCacheManager,
    private val clock: Clock
) : ClaimStateStoreFactory {

    override fun create(key: TokenPoolKey): ClaimStateStore {

        // No existing Store for this key, we need to create one
        // Try and get the existing state from storage
        val stateRecord = stateManager.get(listOf(key.toString()))
            .map { it.value }
            .firstOrNull()

        val claimState = if (stateRecord == null) {
            createClaimStateFromDefaults(key)
        } else {
            createClaimStateFromExisting(key, stateRecord)
        }

        return PerformanceClaimStateStoreImpl(
            key,
            claimState,
            serialization,
            stateManager,
            tokenPoolCacheManager,
            clock
        )
    }

    private fun createClaimStateFromDefaults(key: TokenPoolKey): StoredPoolClaimState {
        val tokenPoolCacheState = getDefaultTokenPoolCacheState(key)
        val stateBytes = serialization.serialize(tokenPoolCacheState)
        val newStoredState = State(
            key = key.toString(),
            value = stateBytes,
            modifiedTime = clock.instant()
        )

        stateManager.create(listOf(newStoredState))

        return StoredPoolClaimState(
            State.VERSION_INITIAL_VALUE,
            key,
            tokenPoolCacheState
        )
    }

    private fun createClaimStateFromExisting(key: TokenPoolKey, existing: State): StoredPoolClaimState {
        return StoredPoolClaimState(
            existing.version,
            key,
            serialization.deserialize(existing.value)
        )
    }

    private fun getDefaultTokenPoolCacheState(key: TokenPoolKey): TokenPoolCacheState {
        return TokenPoolCacheState.newBuilder()
            .setPoolKey(key.toAvro())
            .setAvailableTokens(listOf())
            .setTokenClaims(listOf())
            .build()
    }
}
