package net.corda.ledger.persistence.utxo

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.persistence.utxo.impl.UtxoTransactionOutputDto
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.virtualnode.HoldingIdentity

interface UtxoOutputRecordFactory {
    fun getTokenCacheChangeEventRecords(
        holdingIdentity: HoldingIdentity,
        producedTokens: List<Pair<StateAndRef<*>, UtxoToken>>,
        consumedTokens: List<Pair<StateAndRef<*>, UtxoToken>>
    ): List<Record<TokenPoolCacheKey, TokenPoolCacheEvent>>

    fun getFindTransactionSuccessRecord(
        transactionContainer: SignedTransactionContainer?,
        status: String?,
        externalEventContext: ExternalEventContext,
        serializationService: SerializationService
    ): Record<String, FlowEvent>

    fun getStatesSuccessRecord(
        states: List<UtxoTransactionOutputDto>,
        externalEventContext: ExternalEventContext,
        serializationService: SerializationService
    ): Record<String, FlowEvent>

    fun getPersistTransactionSuccessRecord(
        externalEventContext: ExternalEventContext
    ): Record<String, FlowEvent>
}
