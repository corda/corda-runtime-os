package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.entities.internal.TokenPoolCacheImpl
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.services.TokenCacheEventProcessor
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetricsImpl
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.UTCClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever


class TokenCacheEventProcessorTest {

    private val entityConverter = mock<EntityConverter>()
    private val eventConverter = mock<EventConverter>()
    private val mockHandler = mock<TokenEventHandler<FakeTokenEvent>>()
    private val tokenCacheEventHandlerMap = mutableMapOf<Class<*>, TokenEventHandler<in TokenEvent>>()
    private val event = FakeTokenEvent()
    private val tokenPoolCache = TokenPoolCacheImpl()
    private val cachePoolState = mock<PoolCacheState>()
    private val tokenSelectionMetrics = TokenSelectionMetricsImpl(UTCClock())
    private val externalEventResponseFactory = mock<ExternalEventResponseFactory> {
        on { platformError(any(), any<Throwable>()) } doReturn mock<Record<String, FlowEvent>>()
    }

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
        val target = createTokenCacheEventProcessor()
        whenever(eventConverter.convert(any())).thenThrow(IllegalStateException())

        val result = target.onNext(stateIn, eventIn)

        assertThat(result.responseEvents).isEmpty()
        assertThat(result.updatedState).isSameAs(stateIn)
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `when the event has no payload the event should be sent to the DLQ`() {

        val target =
            TokenCacheEventProcessor(
                eventConverter,
                entityConverter,
                tokenPoolCache,
                tokenCacheEventHandlerMap,
                externalEventResponseFactory,
                tokenSelectionMetrics
            )

        val result = target.onNext(stateIn, eventIn)

        verify(externalEventResponseFactory).platformError(
            eq(
                ExternalEventContext(
                    FakeTokenEvent().externalEventRequestId,
                    FakeTokenEvent().flowId,
                    KeyValuePairList(listOf())
                )
            ),
            any<Throwable>()
        )

        assertThat(result.responseEvents).isNotEmpty()
        assertThat(result.updatedState).isSameAs(stateIn)
        assertThat(result.markForDLQ).isFalse()
    }

    @Test
    fun `when a handler does not exist for the event type send the event to the DLQ`() {
        tokenPoolCacheEvent.payload = 1

        val target =
            TokenCacheEventProcessor(
                eventConverter,
                entityConverter,
                tokenPoolCache,
                tokenCacheEventHandlerMap,
                externalEventResponseFactory,
                tokenSelectionMetrics
            )

        val result = target.onNext(stateIn, eventIn)

        verify(externalEventResponseFactory).platformError(
            eq(
                ExternalEventContext(
                    FakeTokenEvent().externalEventRequestId,
                    FakeTokenEvent().flowId,
                    KeyValuePairList(listOf())
                )
            ),
            any<Throwable>()
        )

        assertThat(result.responseEvents).isNotEmpty()
        assertThat(result.updatedState).isSameAs(stateIn)
        assertThat(result.markForDLQ).isFalse()
    }

    @Test
    fun `forward an event to its handler and return the response`() {
        tokenPoolCacheEvent.payload = "message"

        val outputState = TokenPoolCacheState()
        val handlerResponse = Record<String, FlowEvent>("test", "key1", null)

        val stateIn = TokenPoolCacheState().apply {
            this.poolKey = POOL_CACHE_KEY
            this.availableTokens = listOf()
            this.tokenClaims = listOf()
        }

        whenever(entityConverter.toPoolCacheState(stateIn)).thenReturn(cachePoolState)
        whenever(entityConverter.toTokenPoolKey(POOL_CACHE_KEY)).thenReturn(POOL_KEY)
        whenever(cachePoolState.toAvro()).thenReturn(outputState)
        whenever(mockHandler.handle(any(), eq(cachePoolState), eq(event)))
            .thenReturn(handlerResponse)

        val target = createTokenCacheEventProcessor()

        val result = target.onNext(stateIn, eventIn)

        assertThat(result.responseEvents).hasSize(1)
        assertThat(result.responseEvents.first()).isEqualTo(handlerResponse)
        assertThat(result.updatedState).isSameAs(outputState)
        assertThat(result.markForDLQ).isFalse
    }

    @Test
    fun `null state will be defaulted to an empty state before processing`() {
        tokenPoolCacheEvent.payload = "message"

        val outputState = TokenPoolCacheState()
        val handlerResponse = Record<String, FlowEvent>("test", "key1", null)

        whenever(entityConverter.toPoolCacheState(any())).thenReturn(cachePoolState)
        whenever(cachePoolState.toAvro()).thenReturn(outputState)
        whenever(entityConverter.toTokenPoolKey(POOL_CACHE_KEY)).thenReturn(POOL_KEY)
        whenever(mockHandler.handle(any(), eq(cachePoolState), eq(event)))
            .thenReturn(handlerResponse)

        val target = createTokenCacheEventProcessor()

        val result = target.onNext(null, eventIn)

        val expected = TokenPoolCacheState().apply {
            this.poolKey = POOL_CACHE_KEY
            this.availableTokens = listOf()
            this.tokenClaims = listOf()
        }

        verify(entityConverter).toPoolCacheState(expected)

        assertThat(result.responseEvents).hasSize(1)
        assertThat(result.responseEvents.first()).isEqualTo(handlerResponse)
        assertThat(result.updatedState).isSameAs(outputState)
        assertThat(result.markForDLQ).isFalse
    }

    @Test
    fun `ensure expired claims are removed before calling event handlers`() {
        tokenPoolCacheEvent.payload = "message"

        val outputState = TokenPoolCacheState()
        val handlerResponse = Record<String, FlowEvent>("", "", null)

        val stateIn = TokenPoolCacheState().apply {
            this.poolKey = POOL_CACHE_KEY
            this.availableTokens = listOf()
            this.tokenClaims = listOf()
        }

        whenever(entityConverter.toPoolCacheState(stateIn)).thenReturn(cachePoolState)
        whenever(entityConverter.toTokenPoolKey(POOL_CACHE_KEY)).thenReturn(POOL_KEY)
        whenever(cachePoolState.toAvro()).thenReturn(outputState)
        whenever(mockHandler.handle(any(), eq(cachePoolState), eq(event)))
            .thenReturn(handlerResponse)

        val target = createTokenCacheEventProcessor()

        target.onNext(stateIn, eventIn)

        val inOrder = inOrder(cachePoolState, mockHandler)

        inOrder.verify(cachePoolState).removeExpiredClaims()
        inOrder.verify(mockHandler).handle(any(), any(), any())
    }

    private fun createTokenCacheEventProcessor(): TokenCacheEventProcessor {
        return TokenCacheEventProcessor(
            eventConverter,
            entityConverter,
            tokenPoolCache,
            tokenCacheEventHandlerMap,
            mock(),
            tokenSelectionMetrics
        )
    }

    class FakeTokenEvent : TokenEvent {
        override val externalEventRequestId: String
            get() = "externalEventRequestId-not-set"
        override val flowId: String
            get() = "flowId-not-set"
        override val poolKey: TokenPoolKey
            get() = POOL_KEY
    }
}
