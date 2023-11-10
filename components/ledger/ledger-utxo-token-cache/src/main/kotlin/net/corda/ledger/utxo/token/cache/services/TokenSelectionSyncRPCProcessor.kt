package net.corda.ledger.utxo.token.cache.services

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
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
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.records.Record
import net.corda.tracing.traceStateAndEventExecution
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TokenSelectionSyncRPCProcessor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val eventHandlerMap: Map<Class<*>, TokenEventHandler<in TokenEvent>>,
    private val tokenPoolCache: TokenPoolCache,
    private val claimStateStoreCache: ClaimStateStoreCache,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val tokenSelectionMetrics: TokenSelectionMetrics
) : SyncRPCProcessor<TokenPoolCacheEvent, FlowEvent> {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val eventProcessLock = ReentrantLock()

    override fun process(request: TokenPoolCacheEvent): FlowEvent? {
        val tokenEvent = eventConverter.convert(request)
        return tokenSelectionMetrics.recordProcessingTime(tokenEvent) {
            try {
                val poolKey = entityConverter.toTokenPoolKey(request.poolKey)

                var responseEvent: FlowEvent? = null

                val eventCompletion = eventProcessLock.withLock {
                    val claimStateStore = claimStateStoreCache.get(poolKey)

                    claimStateStore.enqueueRequest { poolState ->

                        val eventProcessingResult = processEvent(
                            StateAndEventProcessor.State(poolState, null),
                            Record("", request.poolKey, request)
                        )

                        val updatedState = checkNotNull(eventProcessingResult.updatedState?.value) {
                            "Unexpected null state returned from event processor."
                        }

                        responseEvent = eventProcessingResult.responseEvents.firstOrNull()?.value as FlowEvent?

                        updatedState
                    }
                }

                val stateWriteSuccess = eventCompletion.get()

                if (!stateWriteSuccess) {
                    externalEventResponseFactory.transientError(
                        ExternalEventContext(
                            tokenEvent.externalEventRequestId,
                            tokenEvent.flowId,
                            KeyValuePairList(listOf())
                        ), IllegalStateException("Failed to save state, version out of sync, please retry.")
                    ).value
                } else {
                    responseEvent
                }
            } catch (ex: Exception) {
                externalEventResponseFactory.platformError(
                    ExternalEventContext(
                        tokenEvent.externalEventRequestId,
                        tokenEvent.flowId,
                        KeyValuePairList(listOf())
                    ), ex
                ).value
            }
        }
    }

    override val requestClass = TokenPoolCacheEvent::class.java

    override val responseClass: Class<FlowEvent> = FlowEvent::class.java

    private fun processEvent(
        state: StateAndEventProcessor.State<TokenPoolCacheState>?,
        event: Record<TokenPoolCacheKey, TokenPoolCacheEvent>,
    ): StateAndEventProcessor.Response<TokenPoolCacheState> {

        val tokenEvent = try {
            eventConverter.convert(event.value)
        } catch (e: Exception) {
            logger.error("Unexpected error while processing event '${event}'. The event will be sent to the DLQ.", e)
            return StateAndEventProcessor.Response(
                state,
                listOf(),
                markForDLQ = true
            )
        }

        logger.debug { "Token event received: $tokenEvent" }

        return traceStateAndEventExecution(event, "Token Event - ${tokenEvent.javaClass.simpleName}") {
            try {
                val nonNullableState = state?.value ?: TokenPoolCacheState().apply {
                    this.poolKey = event.key
                    this.availableTokens = listOf()
                    this.tokenClaims = listOf()
                }

                // Temporary logic that covers the upgrade from release/5.0 to release/5.1
                // The field claimedTokens has been added to the TokenCaim avro object, and it will replace claimedTokenStateRefs.
                // In order to avoid breaking compatibility, the claimedTokenStateRefs has been deprecated, and it will eventually
                // be removed. Any claim that contains a non-empty claimedTokenStateRefs field are considered invalid because
                // this means the avro object is an old one, and it should be replaced by the new format.
                val invalidClaims =
                    nonNullableState.tokenClaims.filterNot { it.claimedTokenStateRefs.isNullOrEmpty() }
                if (invalidClaims.isNotEmpty()) {
                    val invalidClaimsId = invalidClaims.map { it.claimId }
                    logger.warn("Invalid claims were found and have been discarded. Invalid claims: ${invalidClaimsId}")
                }

                val poolKey = entityConverter.toTokenPoolKey(event.key)
                val poolCacheState = entityConverter.toPoolCacheState(nonNullableState)
                val tokenCache = tokenPoolCache.get(poolKey)

                poolCacheState.removeExpiredClaims()

                val handler = checkNotNull(eventHandlerMap[tokenEvent.javaClass]) {
                    "Received an event with and unrecognized payload '${tokenEvent.javaClass}'"
                }

                val result = handler.handle(tokenCache, poolCacheState, tokenEvent)

                logger.debug { "sending token response: $result" }

                if (result == null) {
                    StateAndEventProcessor.Response(
                        StateAndEventProcessor.State(poolCacheState.toAvro(), metadata = state?.metadata),
                        listOf()
                    )
                } else {
                    StateAndEventProcessor.Response(
                        StateAndEventProcessor.State(poolCacheState.toAvro(), metadata = state?.metadata),
                        listOf(result)
                    )
                }
            } catch (e: Exception) {
                val responseMessage = externalEventResponseFactory.platformError(
                    ExternalEventContext(
                        tokenEvent.externalEventRequestId,
                        tokenEvent.flowId,
                        KeyValuePairList(listOf())
                    ),
                    e
                )
                StateAndEventProcessor.Response(state, listOf(responseMessage), markForDLQ = false)
            }
        }
    }
}