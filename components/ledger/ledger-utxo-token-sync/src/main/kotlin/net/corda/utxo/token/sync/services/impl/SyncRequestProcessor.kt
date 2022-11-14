package net.corda.utxo.token.sync.services.impl

import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncMode
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utxo.token.sync.converters.EntityConverter
import net.corda.utxo.token.sync.converters.EventConverter
import net.corda.utxo.token.sync.entities.SyncRequest
import net.corda.utxo.token.sync.handlers.SyncRequestHandler
import net.corda.v5.base.util.contextLogger
import java.time.Instant

class SyncRequestProcessor constructor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val syncRequestHandlerMap: Map<Class<*>, SyncRequestHandler<in SyncRequest>>,
) : StateAndEventProcessor<String, TokenSyncState, TokenSyncEvent> {

    private companion object {
        val log = contextLogger()
    }

    override val keyClass = String::class.java

    override val eventValueClass = TokenSyncEvent::class.java

    override val stateValueClass = TokenSyncState::class.java

    override fun onNext(
        state: TokenSyncState?,
        event: Record<String, TokenSyncEvent>
    ): StateAndEventProcessor.Response<TokenSyncState> {

        try {
            val syncEvent = eventConverter.convert(event.value)

            val nonNullableState = state ?: getDefaultState(checkNotNull(event.value) {
                "Received TokenSyncEvent record with a null value '${event}'"
            })

            val currentState = entityConverter.toCurrentState(nonNullableState)

            val handler = checkNotNull(syncRequestHandlerMap[syncEvent.javaClass]) {
                "Received an event with and unrecognized payload '${syncEvent.javaClass}'"
            }

            val result = handler.handle(currentState, syncEvent)

            return StateAndEventProcessor.Response(
                currentState.toAvro(),
                result
            )
        } catch (e: Exception) {
            log.error("Unexpected error while processing event '${event}'. The event will be sent to the DLQ.", e)
            return StateAndEventProcessor.Response(state, listOf(), markForDLQ = true)
        }
    }

    private fun getDefaultState(tokenSyncEvent: TokenSyncEvent): TokenSyncState {
        return TokenSyncState().apply {
            this.holdingIdentity =
                checkNotNull(tokenSyncEvent.holdingIdentity) { "No holding Identity set '${tokenSyncEvent}'" }
            this.mode = TokenSyncMode.PERIODIC_CHECK
            this.fullSyncState = null
            this.periodcSyncstate = listOf()
            this.nextWakeup = Instant.EPOCH
            this.transientFailureCount = 0
        }
    }
}
