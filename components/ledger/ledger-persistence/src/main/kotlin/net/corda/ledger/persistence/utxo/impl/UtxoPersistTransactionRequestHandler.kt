package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
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
    private val transaction: UtxoTransactionReader,
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
        val isTransactionVerified = transaction.status == TransactionStatus.VERIFIED

        val outputTokenRecords = if (isTransactionVerified) {
            utxoOutputRecordFactory.getTokenCacheChangeEventRecords(
                holdingIdentity,
                transaction.getVisibleStates().values.toList().toTokens(tokenObservers),
                transaction.getConsumedStates(persistenceService).toTokens(tokenObservers)
            )
        } else {
            listOf()
        }

        // persist the transaction
        persistenceService.persistTransaction(transaction)

        // return output records
        return outputTokenRecords + utxoOutputRecordFactory.getPersistTransactionSuccessRecord(externalEventContext)
    }

    private fun List<StateAndRef<ContractState>>.toTokens(tokenObservers: UtxoTokenObserverMap): List<Pair<StateAndRef<*>, UtxoToken>> =
        flatMap { stateAndRef ->
            tokenObservers.getObserverFor(stateAndRef.state.contractStateType).let { observer ->
                if(observer == null) {
                    emptyList()
                } else {
                    try {
                        val token = observer.onCommit(stateAndRef.state.contractState, digestService).let { token ->
                            token.poolKey.tokenType?.let { token } ?: UtxoToken(
                                UtxoTokenPoolKey(
                                    stateAndRef.state.contractStateType.name,
                                    token.poolKey.issuerHash,
                                    token.poolKey.symbol
                                ),
                                token.amount,
                                token.filterFields
                            )
                        }
                        listOf(Pair(stateAndRef, token))
                    } catch (e: Exception) {
                        log.error("Failed while trying call '${this.javaClass}'.onCommit() with '${stateAndRef.state.contractStateType}'")
                        emptyList()
                    }
                }
            }
        }
}
