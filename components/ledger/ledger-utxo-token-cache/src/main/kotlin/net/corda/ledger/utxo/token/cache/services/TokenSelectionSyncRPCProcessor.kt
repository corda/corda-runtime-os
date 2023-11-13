package net.corda.ledger.utxo.token.cache.services

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TokenSelectionSyncRPCProcessor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val tokenPoolCacheManager: TokenPoolCacheManager,
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

        logger.debug { "Token event received: $tokenEvent" }

        return tokenSelectionMetrics.recordProcessingTime(tokenEvent) {
            try {
                val poolKey = entityConverter.toTokenPoolKey(request.poolKey)

                var responseEvent: FlowEvent? = null

                val eventCompletion = eventProcessLock.withLock {
                    val claimStateStore = claimStateStoreCache.get(poolKey)

                    claimStateStore.enqueueRequest { poolState ->

                        val result = tokenPoolCacheManager.processEvent(
                            poolState,
                            poolKey,
                            tokenEvent
                        )

                        logger.debug { "token response: $result" }

                        responseEvent = result.response

                        result.state
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
}