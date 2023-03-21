package net.cordapp.demo.utxo

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "utxo-custom-query-flow-protocol")
class UtxoCustomQueryDemoFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val DUMMY_QUERY_NAME = "UTXO_DUMMY_QUERY"
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.error("Before calling utxoLedgerService")
       val txIds = utxoLedgerService.query(DUMMY_QUERY_NAME, String::class.java)
           .setParameter("testField", "value")
           .setOffset(0)
           .setLimit(100)
           .execute()
           .results

        log.error("Ezeket talaltam bro: $txIds")

        return jsonMarshallingService.format(CustomQueryResponse(txIds))
    }
}

data class CustomQueryResponse( val results: List<String>)
