package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.WakeUpWithException
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE_2
import net.corda.ledger.utxo.token.cache.services.ClaimStateStore
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreCache
import net.corda.ledger.utxo.token.cache.services.StoredPoolClaimState
import net.corda.ledger.utxo.token.cache.services.TokenSelectionDelegatedProcessorImpl
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetricsImpl
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.UTCClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class TokenSelectionDelegatedProcessorImplTest {
    private val testFlowId = "f1"
    private val testExternalEventRequestId = "e1"
    private val storedPoolClaimState = StoredPoolClaimState(0, POOL_KEY, TOKEN_POOL_CACHE_STATE)

    private val tokenEvent = mock<TokenEvent>().apply {
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
    private val processor = mock<StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>>()
    private val claimStateStore = FakeClaimStateStore()
    private val claimStateStoreCache = mock<ClaimStateStoreCache>().apply {
        whenever(get(POOL_KEY)).thenReturn(claimStateStore)
    }
    private val externalEventResponseFactory = mock<ExternalEventResponseFactory>()

    private val target = TokenSelectionDelegatedProcessorImpl(
        eventConverter,
        entityConverter,
        processor,
        claimStateStoreCache,
        externalEventResponseFactory,
        TokenSelectionMetricsImpl(UTCClock())
    )

    @Test
    fun `process success returns flow event from legacy processor`() {
        val returnedEvent = FlowEvent(testFlowId, WakeUpWithException())
        val responseRecord = Record("", "", returnedEvent)
        val processorResponse = StateAndEventProcessor.Response(
            StateAndEventProcessor.State(TOKEN_POOL_CACHE_STATE_2, null),
            listOf(responseRecord)
        )

        claimStateStore.inputPoolState = TOKEN_POOL_CACHE_STATE
        whenever(processor.onNext(any(), any())).thenReturn(processorResponse)

        val result = target.process(tokenPoolCacheEvent)

        assertThat(result).isEqualTo(returnedEvent)

        val expectedLegacyRecord = Record("", POOL_CACHE_KEY, tokenPoolCacheEvent)
        val expectedLegacyState = StateAndEventProcessor.State(TOKEN_POOL_CACHE_STATE, null)
        verify(processor).onNext(eq(expectedLegacyState), eq(expectedLegacyRecord))
    }

    @Test
    fun `process failure (concurrency check) returns transient exception`() {
        val returnedEvent = FlowEvent(testFlowId, WakeUpWithException())
        val responseRecord = Record("", "", returnedEvent)
        val processorResponse = StateAndEventProcessor.Response(
            StateAndEventProcessor.State(TOKEN_POOL_CACHE_STATE_2, null),
            listOf(responseRecord)
        )

        claimStateStore.inputPoolState = TOKEN_POOL_CACHE_STATE
        claimStateStore.completionType = false
        whenever(externalEventResponseFactory.transientError(any(), any<Throwable>())).thenReturn(responseRecord)
        whenever(processor.onNext(any(), any())).thenReturn(processorResponse)

        val result = target.process(tokenPoolCacheEvent)

        assertThat(result).isEqualTo(returnedEvent)

        val expectedExternalEventContext = ExternalEventContext(
            testExternalEventRequestId,
            testFlowId,
            KeyValuePairList(listOf())
        )

        verify(externalEventResponseFactory).transientError(
            eq(expectedExternalEventContext),
            any<IllegalStateException>()
        )
    }

    @Test
    fun `on next failure returns platform exception`() {
        val returnedEvent = FlowEvent(testFlowId, WakeUpWithException())
        val responseRecord = Record("", "", returnedEvent)
        claimStateStore.inputPoolState = TOKEN_POOL_CACHE_STATE
        whenever(externalEventResponseFactory.platformError(any(), any<Throwable>())).thenReturn(responseRecord)
        whenever(processor.onNext(any(), any())).thenThrow(IllegalStateException())

        val result = target.process(tokenPoolCacheEvent)

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

        val result = target.process(tokenPoolCacheEvent)

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