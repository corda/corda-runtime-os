package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.tracing.wrapWithTracingExecutor
import net.corda.utilities.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
class PerformanceClaimStateStoreImpl(
    private val tokenPoolKey: TokenPoolKey,
    private val serialization: TokenPoolCacheStateSerialization,
    private val stateManager: StateManager,
    private val tokenPoolCacheManager: TokenPoolCacheManager,
    private val clock: Clock
) : ClaimStateStore {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // We use a limited queue executor to ensure we only ever queue one new request if we are currently processing
    // an existing request.
    private val executor = wrapWithTracingExecutor(
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(1),
            ThreadPoolExecutor.DiscardPolicy()
        )
    )
    private val requestQueue = LinkedBlockingQueue<QueuedRequestItem>()
    private var currentState = createClaimState()

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
        var requests = drainQueuedRequests()

        while (requests.isNotEmpty()) {
            // Executing all pending requests against the current state
            var currentPoolState = currentState.poolState
            val unexceptionalRequests = mutableListOf<CompletableFuture<Boolean>>()
            requests.forEach { queuedRequest ->
                try {
                    currentPoolState = queuedRequest.requestAction(currentPoolState)
                    unexceptionalRequests.add(queuedRequest.requestFuture)
                } catch (e: Exception) {
                    queuedRequest.requestFuture.completeExceptionally(e)
                }
            }

            // Try and update the state
            val stateManagerState = State(
                tokenPoolKey.toString(),
                serialization.serialize(currentPoolState),
                currentState.dbVersion,
                modifiedTime = clock.instant()
            )

            val mismatchedState = try {
                stateManager.update(listOf(stateManagerState))
                    .map { it.value }
                    .firstOrNull()
            } catch (ex: Exception) {
                logger.warn("Exception during execution of an update", ex)

                // The current batch of requests aborted and the state set to version -1.
                // This will force a refresh of the state when the DB is available.
                State(
                    tokenPoolKey.toString(),
                    stateManagerState.value,
                    -1,
                    modifiedTime = stateManagerState.modifiedTime
                )
            }

            // If we failed to update the state then fail the batch of requests and rollback the state to the DB
            // version
            if (mismatchedState != null) {
                currentState = createClaimStateFromExisting(mismatchedState)

                // When fail to save the state we have to assume the available token cache could be invalid
                // and therefore clear it to force a refresh from the DB on the next request.
                tokenPoolCacheManager.removeAllTokensFromCache(tokenPoolKey)

                unexceptionalRequests.abort()
            } else {
                currentState = currentState.copy(dbVersion = currentState.dbVersion + 1)
                unexceptionalRequests.accept()
            }

            // look for more request to process
            requests = drainQueuedRequests()
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

    private fun createClaimState(): StoredPoolClaimState {
        // No existing Store for this key, we need to create one
        // Try and get the existing state from storage
        val stateRecord = stateManager.get(listOf(tokenPoolKey.toString()))
            .map { it.value }
            .firstOrNull()

        return if (stateRecord == null) {
            createClaimStateFromDefaults()
        } else {
            createClaimStateFromExisting(stateRecord)
        }
    }

    private fun createClaimStateFromDefaults(): StoredPoolClaimState {
        val tokenPoolCacheState = getDefaultTokenPoolCacheState()
        val stateBytes = serialization.serialize(tokenPoolCacheState)
        val newStoredState = State(
            key = tokenPoolKey.toString(),
            value = stateBytes,
            metadata = Metadata(mapOf(STATE_TYPE to tokenPoolCacheState::class.java.name)),
            modifiedTime = clock.instant()
        )

        stateManager.create(listOf(newStoredState))

        return StoredPoolClaimState(
            State.VERSION_INITIAL_VALUE,
            tokenPoolKey,
            tokenPoolCacheState
        )
    }

    private fun createClaimStateFromExisting(existing: State): StoredPoolClaimState {
        return StoredPoolClaimState(
            existing.version,
            tokenPoolKey,
            serialization.deserialize(existing.value)
        )
    }

    private fun getDefaultTokenPoolCacheState(): TokenPoolCacheState {
        return TokenPoolCacheState.newBuilder()
            .setPoolKey(tokenPoolKey.toAvro())
            .setAvailableTokens(listOf())
            .setTokenClaims(listOf())
            .build()
    }
}
