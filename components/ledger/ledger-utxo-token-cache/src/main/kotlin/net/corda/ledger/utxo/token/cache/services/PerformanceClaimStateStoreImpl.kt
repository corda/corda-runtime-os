package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("UNUSED")
class PerformanceClaimStateStoreImpl(
    private val key: TokenPoolKey,
    storedPoolClaimState: StoredPoolClaimState,
    private val serialization: TokenPoolCacheStateSerialization,
    private val stateManager: StateManager,
    private val clock: Clock
) : ClaimStateStore {

    // We use a limited queue executor to ensure we only ever queue one new request if we are currently processing
    // an existing request.
    private val executor = ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(1),
        ThreadPoolExecutor.DiscardPolicy()
    )
    private val requestQueue = LinkedBlockingQueue<QueuedRequestItem>()

    private var currentState = storedPoolClaimState

    private data class QueuedRequestItem(
        val requestAction: (TokenPoolCacheState) -> TokenPoolCacheState,
        val requestFuture: CompletableFuture<Boolean>
    )

    override fun enqueueRequest(request: (TokenPoolCacheState) -> TokenPoolCacheState): CompletableFuture<Boolean> {
        val requestCompletion = CompletableFuture<Boolean>()
        requestQueue.add(QueuedRequestItem(request, requestCompletion))
        CompletableFuture.runAsync(::drainAndProcessQueue, executor)
        return requestCompletion
    }

    private fun drainAndProcessQueue() {
        var itemsToProcess = drainQueuedRequests()

        while (itemsToProcess.isNotEmpty()) {
            // Executing all pending requests against the current state
            var currentPoolState = currentState.poolState
            val unexceptionalRequests = mutableListOf<CompletableFuture<Boolean>>()
            itemsToProcess.forEach { queuedRequest ->
                try {
                    currentPoolState = queuedRequest.requestAction(currentPoolState)
                    unexceptionalRequests.add(queuedRequest.requestFuture)
                } catch (e: Exception) {
                    queuedRequest.requestFuture.completeExceptionally(e)
                }
            }

            // Try and update the state
            val stateManagerState = State(
                key.toString(),
                serialization.serialize(currentPoolState),
                currentState.dbVersion,
                modifiedTime = clock.instant()
            )

            val mismatchedState = stateManager.update(listOf(stateManagerState))
                .map { it.value }
                .firstOrNull()

            // If we failed to update the state then fail the batch of requests and rollback the state to the DB
            // version
            if (mismatchedState != null) {
                currentState = StoredPoolClaimState(
                    dbVersion = mismatchedState.version,
                    key,
                    serialization.deserialize(mismatchedState.value)
                )
                unexceptionalRequests.abort()
            } else {
                currentState = currentState.copy(dbVersion = currentState.dbVersion + 1)
                unexceptionalRequests.accept()
            }

            // look for more request to process
            itemsToProcess = drainQueuedRequests()
        }
    }

    private fun drainQueuedRequests(): List<QueuedRequestItem> {
        return mutableListOf<QueuedRequestItem>().apply {
            requestQueue.drainTo(this)
        }
    }

    private fun List<CompletableFuture<Boolean>>.accept() {
        this.forEach { it.complete(true) }
    }

    private fun List<CompletableFuture<Boolean>>.abort() {
        this.forEach { it.complete(false) }
    }

    private fun TokenPoolCacheState.copy(): TokenPoolCacheState {
        return TokenPoolCacheState.newBuilder()
            .setPoolKey(this.poolKey)
            .setAvailableTokens(this.availableTokens)
            .setTokenClaims(this.tokenClaims)
            .build()
    }
}