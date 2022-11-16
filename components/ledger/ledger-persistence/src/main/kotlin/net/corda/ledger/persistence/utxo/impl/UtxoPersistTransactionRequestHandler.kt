package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityResponse
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoTokenObserverMap
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.UtxoToken

class UtxoPersistTransactionRequestHandler(
    private val transaction: UtxoTransactionReader,
    private val tokenObservers: UtxoTokenObserverMap,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val responseFactory: ResponseFactory
) : RequestHandler {

    private companion object {
        val log = contextLogger()
    }

    override fun execute(): List<Record<*, *>> {
        // Feed all produced and consumed states through any token observers defined for them
        val producedTokens = transaction.getProducedStates().toTokens(tokenObservers)
        val consumedTokens = transaction.getConsumedStates().toTokens(tokenObservers)

        // persist the transaction
        persistenceService.persistTransaction(transaction)

        val persistTransactionSuccessRecord = responseFactory.successResponse(
            externalEventContext,
            EntityResponse(emptyList())
        )

        // return output records
        return UtxoOutputRecordFactoryImpl().getTokenCacheChangeEventRecords(producedTokens, consumedTokens) +
                persistTransactionSuccessRecord
    }

    private fun List<StateAndRef<ContractState>>.toTokens(tokenObservers: UtxoTokenObserverMap): List<UtxoToken> {
        return this.flatMap { stateAndRef ->
            tokenObservers.getObserversFor(stateAndRef.state.javaClass)
                .mapNotNull { observer ->
                    try {
                        observer.onCommit(stateAndRef.state.contractState)
                    } catch (e: Exception) {
                        log.error("Failed while trying call '${this.javaClass}'.onCommit() with '${stateAndRef.state}'")
                        null
                    }
                }
        }
    }
}
