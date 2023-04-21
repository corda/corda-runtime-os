package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.services.TokenCacheEventProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenCacheEventProcessorTest {

    private val entityConverter = mock<EntityConverter>()
    private val eventConverter = mock<EventConverter>()
    private val mockHandler = mock<TokenEventHandler<FakeTokenEvent>>()
    private val tokenCacheEventHandlerMap = mutableMapOf<Class<*>, TokenEventHandler<in TokenEvent>>()
    private val event = FakeTokenEvent()
    private val tokenCache = mock<TokenCache>()
    private val cachePoolState = mock<PoolCacheState>()
    private val stateIn = TokenPoolCacheState()
    private val tokenPoolCacheEvent = TokenPoolCacheEvent(POOL_CACHE_KEY, null)
    private val eventIn = Record(
        "",
        POOL_CACHE_KEY,
        tokenPoolCacheEvent
    )

    @BeforeEach
    fun setup() {
        @Suppress("unchecked_cast")
        tokenCacheEventHandlerMap[FakeTokenEvent::class.java] = mockHandler as TokenEventHandler<in TokenEvent>
        whenever(eventConverter.convert(tokenPoolCacheEvent)).thenReturn(event)
    }

    @Test
    fun `when an unexpected processing exception is thrown the event will be sent to the DLQ`() {
        val target = TokenCacheEventProcessor(eventConverter, entityConverter, tokenCacheEventHandlerMap)
        whenever(eventConverter.convert(any())).thenThrow(IllegalStateException())

        val result = target.onNext(stateIn, eventIn)

        assertThat(result.responseEvents).isEmpty()
        assertThat(result.updatedState).isSameAs(stateIn)
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `when the event has no payload the event should be sent to the DLQ`() {

        val target = TokenCacheEventProcessor(eventConverter, entityConverter, tokenCacheEventHandlerMap)

        val result = target.onNext(stateIn, eventIn)

        assertThat(result.responseEvents).isEmpty()
        assertThat(result.updatedState).isSameAs(stateIn)
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `when a handler does not exist for the event type send the event to the DLQ`() {
        tokenPoolCacheEvent.payload = 1

        val target = TokenCacheEventProcessor(eventConverter, entityConverter, tokenCacheEventHandlerMap)

        val result = target.onNext(stateIn, eventIn)

        assertThat(result.responseEvents).isEmpty()
        assertThat(result.updatedState).isSameAs(stateIn)
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `forward an event to its handler and return the response`() {
        tokenPoolCacheEvent.payload = "message"

        val outputState = TokenPoolCacheState()
        val handlerResponse = Record<String, FlowEvent>("", "", null)

        whenever(entityConverter.toTokenCache(stateIn)).thenReturn(tokenCache)
        whenever(entityConverter.toPoolCacheState(stateIn)).thenReturn(cachePoolState)
        whenever(cachePoolState.toAvro()).thenReturn(outputState)
        whenever(mockHandler.handle(tokenCache, cachePoolState, event))
            .thenReturn(handlerResponse)

        val target = TokenCacheEventProcessor(eventConverter, entityConverter, tokenCacheEventHandlerMap)

        val result = target.onNext(stateIn, eventIn)

        assertThat(result.responseEvents).hasSize(1)
        assertThat(result.responseEvents.first()).isSameAs(handlerResponse)
        assertThat(result.updatedState).isSameAs(outputState)
        assertThat(result.markForDLQ).isFalse
    }

    @Test
    fun `null state will be defaulted to an empty state before processing`() {
        tokenPoolCacheEvent.payload = "message"

        val outputState = TokenPoolCacheState()
        val handlerResponse = Record<String, FlowEvent>("", "", null)

        whenever(entityConverter.toTokenCache(any())).thenReturn(tokenCache)
        whenever(entityConverter.toPoolCacheState(any())).thenReturn(cachePoolState)
        whenever(cachePoolState.toAvro()).thenReturn(outputState)
        whenever(mockHandler.handle(tokenCache, cachePoolState, event))
            .thenReturn(handlerResponse)

        val target = TokenCacheEventProcessor(eventConverter, entityConverter, tokenCacheEventHandlerMap)

        val result = target.onNext(null, eventIn)

        val expected = TokenPoolCacheState().apply {
            this.poolKey = POOL_CACHE_KEY
            this.availableTokens = listOf()
            this.tokenClaims = listOf()
        }

        verify(entityConverter).toPoolCacheState(expected)

        assertThat(result.responseEvents).hasSize(1)
        assertThat(result.responseEvents.first()).isSameAs(handlerResponse)
        assertThat(result.updatedState).isSameAs(outputState)
        assertThat(result.markForDLQ).isFalse
    }

    class FakeTokenEvent : TokenEvent {
        override val poolKey: TokenPoolCacheKey
            get() = POOL_CACHE_KEY

    }
}
