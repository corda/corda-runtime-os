package net.corda.ledger.utxo.token.cache.services

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

class TokenCacheEventProcessor constructor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val tokenPoolCache: TokenPoolCache,
    private val tokenCacheEventHandlerMap: Map<Class<*>, TokenEventHandler<in TokenEvent>>,
    private val externalEventResponseFactory: ExternalEventResponseFactory
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

        val tokenEvent = try {
            eventConverter.convert(event.value)
        } catch (e: Exception) {
            log.error("Unexpected error while processing event '${event}'. The event will be sent to the DLQ.", e)
            return StateAndEventProcessor.Response(state, listOf(), markForDLQ = true)
        }

        return try {
            val nonNullableState = state ?: TokenPoolCacheState().apply {
                this.poolKey = event.key
                this.availableTokens = listOf()
                this.tokenClaims = listOf()
            }

            // Temporary logic that covers the upgrade from release/5.0 to release/5.1
            // The field claimedTokens has been added to the TokenCaim avro object, and it will replace claimedTokenStateRefs.
            // In order to avoid breaking compatibility, the claimedTokenStateRefs has been deprecated, and it will eventually
            // be removed. Any claim that contains a non-empty claimedTokenStateRefs field are considered invalid because
            // this means the avro object is an old one, and it should be replaced by the new format.
            val validClaims =
                nonNullableState.tokenClaims.filter { it.claimedTokenStateRefs.isNullOrEmpty() }
            val invalidClaims = nonNullableState.tokenClaims - validClaims.toSet()
            if (invalidClaims.isNotEmpty()) {
                val invalidClaimsId = invalidClaims.map { it.claimId }
                log.warn("Invalid claims were found and have been discarded. Invalid claims: ${invalidClaimsId}")
            }

            val poolKey = entityConverter.toTokenPoolKey(event.key)
            val poolCacheState = entityConverter.toPoolCacheState(nonNullableState)
            val tokenCache = tokenPoolCache.get(poolKey)

            val handler = checkNotNull(tokenCacheEventHandlerMap[tokenEvent.javaClass]) {
                "Received an event with and unrecognized payload '${tokenEvent.javaClass}'"
            }

            val result = handler.handle(tokenCache, poolCacheState, tokenEvent)
                ?: return StateAndEventProcessor.Response(poolCacheState.toAvro(), listOf())

            StateAndEventProcessor.Response(
                poolCacheState.toAvro(),
                listOf(result)
            )
        } catch (e: Exception) {
            val responseMessage = externalEventResponseFactory.platformError(
                ExternalEventContext(
                    tokenEvent.externalEventRequestId,
                    tokenEvent.flowId,
                    KeyValuePairList(listOf())
                ),
                e
            )
            StateAndEventProcessor.Response(state, listOf(responseMessage), markForDLQ = true)
        }
    }
}
