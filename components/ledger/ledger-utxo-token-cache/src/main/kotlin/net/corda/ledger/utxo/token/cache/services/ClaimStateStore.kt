package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import java.util.concurrent.CompletableFuture

interface ClaimStateStore {
    /**
     * Enqueues a request to modify the TokenPoolCacheState. The queues requests will be dispatched synchronously.
     *
     * @param request The function used to modify the [TokenPoolCacheState]
     * @return A future that is completed once the request is complete, the future returns true if the state was
     * modified and persisted correctly, false if the state update failed due to a concurrency check.
     */
    fun enqueueRequest(request: (TokenPoolCacheState) -> TokenPoolCacheState): CompletableFuture<Boolean>
}
