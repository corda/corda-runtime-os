package net.corda.ledger.utxo.token.cache.services

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class TokenSelectionSyncRPCProcessor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val tokenPoolCacheManager: TokenPoolCacheManager,
    private val claimStateStoreCache: ClaimStateStoreCache,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val tokenSelectionMetrics: TokenSelectionMetrics
) : SyncRPCProcessor<TokenPoolCacheEvent, FlowEvent> {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val eventProcessLock = ReentrantLock()

    override fun process(request: TokenPoolCacheEvent): FlowEvent? {
        val tokenEvent = eventConverter.convert(request)

        logger.debug { "Token event received: $tokenEvent" }

        return tokenSelectionMetrics.recordProcessingTime(tokenEvent) {
            try {

                var responseEvent: FlowEvent? = null

                val eventCompletion = eventProcessLock.withLock {
                    val claimStateStore = claimStateStoreCache.get(tokenEvent.poolKey)

                    claimStateStore.enqueueRequest { poolState ->

                        val state = entityConverter.toPoolCacheState(poolState)
                        val result = tokenPoolCacheManager.processEvent(
                            state,
                            tokenEvent
                        )

                        logger.debug { "token response: $result" }

                        responseEvent = result.response

                        result.state.toAvro()
                    }
                }

                val stateWriteSuccess = eventCompletion.get()

                if (stateWriteSuccess) {
                    responseEvent
                } else {
                    throw CordaHTTPServerTransientException(
                        tokenEvent.externalEventRequestId,
                        IllegalStateException("Failed to save state, version out of sync, please retry.")
                    )
                }
            } catch (e: CordaHTTPServerTransientException) {
                throw e
            } catch (exception: Exception) {
                externalEventResponseFactory.platformError(tokenEvent, exception)
            }
        }
    }

    private fun ExternalEventResponseFactory.platformError(tokenEvent: TokenEvent, exception: Exception) =
        platformError(createExternalEventContext(tokenEvent), exception).value

    private fun createExternalEventContext(tokenEvent: TokenEvent) =
        ExternalEventContext(
            tokenEvent.externalEventRequestId,
            tokenEvent.flowId,
            KeyValuePairList(listOf())
        )

    override val requestClass = TokenPoolCacheEvent::class.java

    override val responseClass: Class<FlowEvent> = FlowEvent::class.java
}
