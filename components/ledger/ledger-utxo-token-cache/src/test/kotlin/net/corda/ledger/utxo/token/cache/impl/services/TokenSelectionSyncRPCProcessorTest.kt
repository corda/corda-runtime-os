package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.WakeUpWithException
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE_2
import net.corda.ledger.utxo.token.cache.services.ClaimStateStore
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreCache
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheManager
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetricsImpl
import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncRPCProcessor
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class TokenSelectionSyncRPCProcessorTest {
    private val testFlowId = "f1"
    private val testExternalEventRequestId = "e1"

    private val tokenEvent = mock<TokenEvent>().apply {
        whenever(this.poolKey).thenReturn(POOL_KEY)
        whenever(this.flowId).thenReturn(testFlowId)
        whenever(this.externalEventRequestId).thenReturn(testExternalEventRequestId)
    }
    private val tokenPoolCacheEvent = TokenPoolCacheEvent(POOL_CACHE_KEY, TokenClaimQuery())

    private val eventConverter = mock<EventConverter>().apply {
        whenever(convert(tokenPoolCacheEvent)).thenReturn(tokenEvent)
    }
    private val entityConverter = mock<EntityConverter>().apply {
        whenever(toTokenPoolKey(POOL_CACHE_KEY)).thenReturn(POOL_KEY)
    }
    private val claimStateStore = FakeClaimStateStore()
    private val claimStateStoreCache = mock<ClaimStateStoreCache>().apply {
        whenever(get(POOL_KEY)).thenReturn(claimStateStore)
    }
    private val externalEventResponseFactory = mock<ExternalEventResponseFactory>()
    private val tokenPoolCacheManager = mock<TokenPoolCacheManager>()
    private val POOL_CACHE_STATE = mock<PoolCacheState>()
    private val POOL_CACHE_STATE_2 = mock<PoolCacheState>()

    private val tokenSelectionSyncRPCProcessor =
        TokenSelectionSyncRPCProcessor(
            eventConverter,
            entityConverter,
            tokenPoolCacheManager,
            claimStateStoreCache,
            externalEventResponseFactory,
            TokenSelectionMetricsImpl()
        )

    @Test
    fun `process successfully returns flow event`() {
        // Test setup
        val returnedEvent = FlowEvent(testFlowId, WakeUpWithException())
        val processorResponse = TokenPoolCacheManager.ResponseAndState(
            returnedEvent,
            POOL_CACHE_STATE_2
        )
        whenever(entityConverter.toPoolCacheState(TOKEN_POOL_CACHE_STATE_2)).thenReturn(POOL_CACHE_STATE_2)

        claimStateStore.inputPoolState = TOKEN_POOL_CACHE_STATE_2
        whenever(tokenPoolCacheManager.processEvent(any(), any())).thenReturn(processorResponse)

        // Process the event
        val result = tokenSelectionSyncRPCProcessor.process(tokenPoolCacheEvent)

        // Ensure the that the expected result is resulted after processing the event
        assertThat(result).isEqualTo(returnedEvent)

        // Ensure the correct arguments are passed to the tokenPoolCacheManager
        val expectedState = POOL_CACHE_STATE_2
        verify(tokenPoolCacheManager).processEvent(eq(expectedState), eq(tokenEvent))
    }

    @Test
    fun `process failure (concurrency check) returns transient exception`() {
        val returnedEvent = FlowEvent(testFlowId, WakeUpWithException())
        val processorResponse = TokenPoolCacheManager.ResponseAndState(
            returnedEvent,
            POOL_CACHE_STATE
        )
        whenever(entityConverter.toPoolCacheState(TOKEN_POOL_CACHE_STATE)).thenReturn(POOL_CACHE_STATE)

        claimStateStore.inputPoolState = TOKEN_POOL_CACHE_STATE
        claimStateStore.completionType = false
        whenever(tokenPoolCacheManager.processEvent(any(), any())).thenReturn(processorResponse)

        val e = assertThrows<CordaHTTPServerTransientException> {
            tokenSelectionSyncRPCProcessor.process(tokenPoolCacheEvent)
        }

        assertThat(e.requestId).isEqualTo(testExternalEventRequestId)
        assertThat(e.cause!!.javaClass).isEqualTo(IllegalStateException::class.java)
        assertThat(e.cause!!.message).isEqualTo("Failed to save state, version out of sync, please retry.")
    }

    @Test
    fun `on next failure returns platform exception`() {
        val returnedEvent = FlowEvent(testFlowId, WakeUpWithException())
        val responseRecord = Record("", "", returnedEvent)
        claimStateStore.inputPoolState = TOKEN_POOL_CACHE_STATE
        whenever(externalEventResponseFactory.platformError(any(), any<Throwable>())).thenReturn(responseRecord)

        val result = tokenSelectionSyncRPCProcessor.process(tokenPoolCacheEvent)

        assertThat(result).isEqualTo(returnedEvent)

        val expectedExternalEventContext = ExternalEventContext(
            testExternalEventRequestId,
            testFlowId,
            KeyValuePairList(listOf())
        )
        verify(externalEventResponseFactory).platformError(
            eq(expectedExternalEventContext),
            any<ExecutionException>()
        )
    }

    @Test
    fun `process failure returns platform exception`() {
        val returnedEvent = FlowEvent(testFlowId, WakeUpWithException())
        val errorRecord = Record("", "", returnedEvent)

        whenever(claimStateStoreCache.get(any())).thenThrow(IllegalStateException())
        whenever(externalEventResponseFactory.platformError(any(), any<Exception>())).thenReturn(errorRecord)

        val result = tokenSelectionSyncRPCProcessor.process(tokenPoolCacheEvent)

        assertThat(result).isEqualTo(returnedEvent)

        val expectedExternalEventContext = ExternalEventContext(
            testExternalEventRequestId,
            testFlowId,
            KeyValuePairList(listOf())
        )
        verify(externalEventResponseFactory).platformError(
            eq(expectedExternalEventContext),
            any<IllegalStateException>()
        )
    }

    private class FakeClaimStateStore : ClaimStateStore {
        var completionType = true
        var inputPoolState: TokenPoolCacheState? = null
        var outputPoolState: TokenPoolCacheState? = null

        override fun enqueueRequest(request: (TokenPoolCacheState) -> TokenPoolCacheState): CompletableFuture<Boolean> {
            return try {
                outputPoolState = request(inputPoolState!!)
                CompletableFuture<Boolean>().apply { complete(completionType) }
            } catch (e: Exception) {
                CompletableFuture<Boolean>().apply { completeExceptionally(e) }
            }
        }
    }
}
