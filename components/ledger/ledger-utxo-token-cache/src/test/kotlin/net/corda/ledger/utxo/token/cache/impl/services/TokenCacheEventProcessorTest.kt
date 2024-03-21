package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.entities.internal.TokenPoolCacheImpl
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheManager
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration

class TokenCacheEventProcessorTest {

    private val mockHandler = mock<TokenEventHandler<FakeTokenEvent>>()
    private val tokenCacheEventHandlerMap = mutableMapOf<Class<*>, TokenEventHandler<in TokenEvent>>()
    private val event = FakeTokenEvent()
    private val tokenPoolCache = TokenPoolCacheImpl(Duration.ZERO)
    private val cachePoolState = mock<PoolCacheState>()

    @BeforeEach
    fun setup() {
        @Suppress("unchecked_cast")
        tokenCacheEventHandlerMap[FakeTokenEvent::class.java] = mockHandler as TokenEventHandler<in TokenEvent>
    }

    @Test
    fun `ensure a state and response are returned when an event is processed correctly`() {
        val handlerResponse = Record("", "", FlowEvent())

        whenever(mockHandler.handle(any(), eq(cachePoolState), eq(event))).thenReturn(handlerResponse)

        val tokenPoolCacheManager = createTokenPoolCacheManager()

        val result = tokenPoolCacheManager.processEvent(cachePoolState, event)

        assertThat(result.state).isSameAs(cachePoolState)
        assertThat(result.response).isSameAs(handlerResponse.value)
    }

    @Test
    fun `ensure expired and invalid claims are removed before calling event handlers`() {
        val handlerResponse = Record("", "", FlowEvent())

        whenever(mockHandler.handle(any(), eq(cachePoolState), eq(event))).thenReturn(handlerResponse)

        val tokenPoolCacheManager = createTokenPoolCacheManager()

        tokenPoolCacheManager.processEvent(cachePoolState, event)

        val inOrder = inOrder(cachePoolState, mockHandler)

        inOrder.verify(cachePoolState).removeInvalidClaims()
        inOrder.verify(cachePoolState).removeExpiredClaims()
        inOrder.verify(mockHandler).handle(any(), any(), any())
    }

    private fun createTokenPoolCacheManager(): TokenPoolCacheManager {
        return TokenPoolCacheManager(
            tokenPoolCache,
            tokenCacheEventHandlerMap
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
