package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import java.util.concurrent.CompletableFuture

interface ClaimStateStore {
    fun enqueueRequest(request: (TokenPoolCacheState) -> TokenPoolCacheState):CompletableFuture<Boolean>
}