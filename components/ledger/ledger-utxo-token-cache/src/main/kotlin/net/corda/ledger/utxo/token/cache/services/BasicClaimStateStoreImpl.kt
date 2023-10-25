package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.time.Clock
import java.util.concurrent.CompletableFuture

@Suppress("UNUSED")
class BasicClaimStateStoreImpl(
    private val key: TokenPoolKey,
    storedPoolClaimState: StoredPoolClaimState,
    private val serialization: TokenPoolCacheStateSerialization,
    private val stateManager: StateManager,
    private val clock: Clock
) : ClaimStateStore {

    private var currentState = storedPoolClaimState

    override fun enqueueRequest(request: (TokenPoolCacheState) -> TokenPoolCacheState): CompletableFuture<Boolean> {
        val newState = currentState.copy(poolState = request(currentState.poolState))
        return write(newState)
    }

    private fun write(state: StoredPoolClaimState): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        try {
            val stateManagerState = State(
                key.toString(),
                serialization.serialize(state.poolState),
                state.dbVersion,
                Metadata(),
                clock.instant()
            )

            val mismatchedState = stateManager.update(listOf(stateManagerState))
                .map { it.value }
                .firstOrNull()

            if (mismatchedState != null) {
                currentState = StoredPoolClaimState(
                    dbVersion = mismatchedState.version,
                    key,
                    serialization.deserialize(mismatchedState.value)
                )
                future.complete(false)
            } else {
                currentState = currentState.copy(dbVersion = currentState.dbVersion + 1, poolState = state.poolState)
                future.complete(true)
            }
        } catch (ex: Exception) {
            future.completeExceptionally(ex)
        }

        return future
    }
}