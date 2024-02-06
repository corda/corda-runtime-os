package net.corda.ledger.persistence.utxo

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.messaging.api.records.Record
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
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
    ): Record<String, FlowEvent>

    fun getFindSignedLedgerTransactionSuccessRecord(
        transactionContainer: SignedLedgerTransactionContainer?,
        status: String?,
        externalEventContext: ExternalEventContext,
    ): Record<String, FlowEvent>

    fun getStatesSuccessRecord(
        states: List<UtxoVisibleTransactionOutputDto>,
        externalEventContext: ExternalEventContext,
    ): Record<String, FlowEvent>

    fun getPersistTransactionSuccessRecord(
        externalEventContext: ExternalEventContext
    ): Record<String, FlowEvent>

    fun getFindFilteredTransactionsAndSignaturesSuccessRecord(
        filteredTransactionsAndSignatures: Map<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>>,
        externalEventContext: ExternalEventContext
    ): Record<String, FlowEvent>
}
