package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistFilteredTransaction
import net.corda.ledger.common.data.transaction.FilteredTransactionContainer
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoTokenObserverMap
import net.corda.ledger.persistence.utxo.impl.TokenStateObserverContextImpl
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.messaging.api.records.Record
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.TokenStateObserverContext
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import org.slf4j.LoggerFactory

class UtxoPersistFilteredTransactionRequestHandler @Suppress("LongParameterList") constructor(
    private val request: PersistFilteredTransaction,
    private val tokenObservers: UtxoTokenObserverMap,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory,
    private val serializationService: SerializationService,
    private val digestService: DigestService
) : RequestHandler {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): List<Record<*, *>> {

        val filteredTransactionContainer = serializationService.deserialize<FilteredTransactionContainer>(request.transaction.array())
        val visibleStates = getVisibleStates(filteredTransactionContainer)
        val listOfPairsStateAndUtxoToken = getTokens(visibleStates.values.toList(), tokenObservers)
        val utxoTokenMap = listOfPairsStateAndUtxoToken.associate { it.first.ref to it.second }

        // persist the transaction
        persistenceService.persistFilteredTransaction(filteredTransactionContainer, visibleStates, utxoTokenMap)

        // return output records
        return listOf(utxoOutputRecordFactory.getPersistTransactionSuccessRecord(externalEventContext))
    }

    fun getVisibleStates(filteredTransactionContainer: FilteredTransactionContainer): Map<Int, StateAndRef<ContractState>> {
        val visibleStatesSet = request.visibleStatesIndexes.toSet()
        val outputs = filteredTransactionContainer.wireTransaction.componentGroups[UtxoComponentGroup.OUTPUTS.ordinal]
        val outputInfos = filteredTransactionContainer.wireTransaction.componentGroups[UtxoComponentGroup.OUTPUTS_INFO.ordinal]

        return outputs
            ?.filter { (index, _) -> index in visibleStatesSet }
            ?.mapValues { (index, output) ->
                UtxoVisibleTransactionOutputDto(
                    transactionId = filteredTransactionContainer.id.toString(),
                    leafIndex = index,
                    info = requireNotNull(outputInfos?.let { group -> group[index] }) {
                        "Output info for output state ${filteredTransactionContainer.id}:$index was not found in the filtered transaction"
                    },
                    data = output
                ).toStateAndRef(serializationService)
            } ?: emptyMap()
    }

    private fun getTokens(
        visibleStates: List<StateAndRef<ContractState>>,
        tokenObservers: UtxoTokenObserverMap
    ): List<Pair<StateAndRef<*>, UtxoToken>> =
        visibleStates.flatMap { stateAndRef ->
            val observerV2 = tokenObservers.getObserverForV2(stateAndRef.state.contractStateType)
            if (observerV2 != null) {
                return@flatMap  onCommit(observerV2, stateAndRef) { obs, context ->
                    obs.onCommit(context)
                }
            }

            // No observer with the new interface was found
            // Look for an observer that implements the deprecated interface
            val observer = tokenObservers.getObserverFor(stateAndRef.state.contractStateType)
            if (observer != null) {
                return@flatMap onCommit(observer, stateAndRef) { obs, context ->
                    @Suppress("deprecation", "removal")
                    obs.onCommit(
                        context.stateAndRef.state.contractState,
                        context.digestService
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
        observerOnCommitCallBlock: (T, TokenStateObserverContext<ContractState>) -> UtxoToken
    ): List<Pair<StateAndRef<*>, UtxoToken>> {
        return try {
            val token = observerOnCommitCallBlock(observer, TokenStateObserverContextImpl(stateAndRef, digestService)).let { token ->
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
