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
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * In the future the legacy Kaka RPC path will be removed and the event processing pipelined merged into a single
 * HTTP path. For now both are supported.
 * CORE-17955
 */
@Suppress("LongParameterList")
class TokenSelectionDelegatedProcessorImpl(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val processor: StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>,
    private val claimStateStoreCache: ClaimStateStoreCache,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val tokenSelectionMetrics: TokenSelectionMetrics
) : TokenSelectionDelegatedProcessor {

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

                        val eventProcessingResult = processor.onNext(
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
}