package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.states.Asset
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory


class QueryFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService


    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("QueryFlow.call() called")

        try {
            val unconsumedStates : List<Asset> = ledgerService.findUnconsumedStatesByType(Asset::class.java).map { it.state.contractState }
            return jsonMarshallingService.format(
                unconsumedStates.map { AssetTO(it.owner.toString().substringAfter("[").substringBefore("]"), it.assetName, it.assetId )})

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}

data class AssetTO(
    val owner: String,
    val assetName: String,
    val assetId: String
)