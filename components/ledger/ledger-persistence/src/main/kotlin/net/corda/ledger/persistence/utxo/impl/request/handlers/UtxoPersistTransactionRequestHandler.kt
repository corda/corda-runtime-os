package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoTokenObserverMap
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.messaging.api.records.Record
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory

class UtxoPersistTransactionRequestHandler @Suppress("LongParameterList") constructor(
    private val holdingIdentity: HoldingIdentity,
    private val transactionReader: UtxoTransactionReader,
    private val tokenObservers: UtxoTokenObserverMap,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory,
    private val digestService: DigestService
) : RequestHandler {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): List<Record<*, *>> {

        val listOfPairsStateAndUtxoToken =
            getTokens(transactionReader.getVisibleStates().values.toList(), tokenObservers)
        val outputTokenRecords = getOutputTokenRecords(listOfPairsStateAndUtxoToken)
        val utxoTokenMap = listOfPairsStateAndUtxoToken.associate { it.first.ref to it.second }

        // persist the transaction
        persistenceService.persistTransaction(transactionReader, utxoTokenMap)

        // return output records
        return outputTokenRecords + utxoOutputRecordFactory.getPersistTransactionSuccessRecord(externalEventContext)
    }

    private fun getOutputTokenRecords(
        listOfPairsStateAndUtxoToken: List<Pair<StateAndRef<*>, UtxoToken>>
    ): List<Record<TokenPoolCacheKey, TokenPoolCacheEvent>> {
        val isTransactionVerified = transactionReader.status == TransactionStatus.VERIFIED
        if (!isTransactionVerified) {
            return listOf()
        }

        return utxoOutputRecordFactory.getTokenCacheChangeEventRecords(
            holdingIdentity,
            listOfPairsStateAndUtxoToken,
            getTokens(transactionReader.getConsumedStates(persistenceService), tokenObservers)
        )
    }

    private fun getTokens(
        visibleStates: List<StateAndRef<ContractState>>,
        tokenObservers: UtxoTokenObserverMap
    ): List<Pair<StateAndRef<*>, UtxoToken>> =
        visibleStates.flatMap { stateAndRef ->
            val observer = tokenObservers.getObserverFor(stateAndRef.state.contractStateType)
            if (observer != null) {
                return@flatMap onCommit(observer, stateAndRef) { obs, sAndRef ->
                    obs.onCommit(
                        sAndRef.state.contractState,
                        digestService
                    )
                }
            }

            // No observer with the deprecated interface was found
            // Look for an observer that implements the new interface
            val observerV2 = tokenObservers.getObserverForV2(stateAndRef.state.contractStateType)
            if (observerV2 != null) {
                return@flatMap  onCommit(observerV2, stateAndRef) { obs, sAndRef ->
                    obs.onCommit(
                        sAndRef,
                        transactionReader.getUtxoTransaction(persistenceService),
                        digestService
                    )
                }
            }

            // No observer found
            // Return an empty list of tokens
            emptyList()
        }

    private fun<T> onCommit(
        observer: T,
        stateAndRef: StateAndRef<ContractState>,
        observerOnCommitCallBlock: (T,  StateAndRef<ContractState>) -> UtxoToken
    ): List<Pair<StateAndRef<*>, UtxoToken>> {
        return try {
            val token = observerOnCommitCallBlock(observer, stateAndRef).let { token ->
                if (token.poolKey.tokenType != null) {
                    token
                } else {
                    createUtxoToken(token, stateAndRef)
                }
            }
            listOf(Pair(stateAndRef, token))
        } catch (e: Exception) {
            log.error("Failed while trying call '${this.javaClass}'.onCommit() with '${stateAndRef.state.contractStateType}'")
            emptyList()
        }
    }

    private fun createUtxoToken(token: UtxoToken, stateAndRef: StateAndRef<ContractState>) =
        UtxoToken(
            UtxoTokenPoolKey(
                stateAndRef.state.contractStateType.name,
                token.poolKey.issuerHash,
                token.poolKey.symbol
            ),
            token.amount,
            token.filterFields
        )
}
