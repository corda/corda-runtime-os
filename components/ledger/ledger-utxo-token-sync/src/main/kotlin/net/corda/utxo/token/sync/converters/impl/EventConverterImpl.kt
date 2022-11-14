package net.corda.utxo.token.sync.converters.impl

import net.corda.data.ledger.utxo.token.selection.data.TokenFullSyncRequest
import net.corda.data.ledger.utxo.token.selection.data.TokenSyncWakeUp
import net.corda.data.ledger.utxo.token.selection.data.TokenUnspentSyncCheck
import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.utxo.token.sync.converters.EntityConverter
import net.corda.utxo.token.sync.converters.EventConverter
import net.corda.utxo.token.sync.entities.SyncRequest

class EventConverterImpl(private val entityConverter: EntityConverter) : EventConverter {

    override fun convert(tokenSyncEvent: TokenSyncEvent?): SyncRequest {
        val event = checkNotNull(tokenSyncEvent) { "The received TokenSyncEvent is null." }
        val key = event.holdingIdentity

        return when (val payload =
            checkNotNull(event.payload) { "The received TokenSyncEvent payload is null." }) {
            is TokenSyncWakeUp -> {
                entityConverter.toWakeUp(key)
            }

            is TokenFullSyncRequest -> {
                entityConverter.toFullSyncRequest(key)
            }

            is TokenUnspentSyncCheck -> {
                entityConverter.toUnspentSyncCheck(key, payload)
            }
            else -> {
                error("The event payload tokenType '${payload.javaClass}' is not supported. Found in event '${tokenSyncEvent}'")
            }
        }
    }
}
