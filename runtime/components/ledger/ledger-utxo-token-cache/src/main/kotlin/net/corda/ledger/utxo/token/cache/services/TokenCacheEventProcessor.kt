package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

class TokenCacheEventProcessor constructor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val tokenCacheEventHandlerMap: Map<Class<*>, TokenEventHandler<in TokenEvent>>,
) : StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass = TokenPoolCacheKey::class.java

    override val eventValueClass = TokenPoolCacheEvent::class.java

    override val stateValueClass = TokenPoolCacheState::class.java

    override fun onNext(
        state: TokenPoolCacheState?,
        event: Record<TokenPoolCacheKey, TokenPoolCacheEvent>
    ): StateAndEventProcessor.Response<TokenPoolCacheState> {

        try {
            val tokenEvent = eventConverter.convert(event.value)

            val nonNullableState = state ?: TokenPoolCacheState().apply {
                this.poolKey = event.key
                this.availableTokens = listOf()
                this.tokenClaims = listOf()
            }

            val tokenCache = entityConverter.toTokenCache(nonNullableState)
            val poolCacheState = entityConverter.toPoolCacheState(nonNullableState)

            val handler = checkNotNull(tokenCacheEventHandlerMap[tokenEvent.javaClass]) {
                "Received an event with and unrecognized payload '${tokenEvent.javaClass}'"
            }

            val result = handler.handle(tokenCache, poolCacheState, tokenEvent)
                ?: return StateAndEventProcessor.Response(poolCacheState.toAvro(), listOf())

            return StateAndEventProcessor.Response(
                poolCacheState.toAvro(),
                listOf(result)
            )
        } catch (e: Exception) {
            log.error("Unexpected error while processing event '${event}'. The event will be sent to the DLQ.", e)
            return StateAndEventProcessor.Response(state, listOf(), markForDLQ = true)
        }
    }
}
