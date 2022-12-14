package net.corda.ledger.persistence.utxo

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.UtxoToken

interface UtxoOutputRecordFactory {
    fun getTokenCacheChangeEventRecords(
        producedTokens: List<UtxoToken>,
        consumedTokens: List<UtxoToken>
    ): List<Record<TokenPoolCacheKey, TokenPoolCacheEvent>>

    fun getFindTransactionSuccessRecord(
        transactionContainer: SignedTransactionContainer?,
        externalEventContext: ExternalEventContext,
        serializationService: SerializationService
    ): Record<String, FlowEvent>

    fun getFindTransactionRelevantStatesSuccessRecord(
        relevantStates: List<StateAndRef<*>>,
        externalEventContext: ExternalEventContext,
        serializationService: SerializationService
    ): Record<String, FlowEvent>

    fun getPersistTransactionSuccessRecord(
        externalEventContext: ExternalEventContext
    ): Record<String, FlowEvent>
}
