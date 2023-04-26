package net.cordapp.demo.utxo

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService

@InitiatingFlow(protocol = "utxo-custom-query-flow-protocol")
class UtxoCustomQueryDemoFlow : ClientStartableFlow {

    data class CustomQueryFlowRequest(
        val offset: Int,
        val limit: Int
    )
    data class CustomQueryFlowResponse(val results: List<String>)

    private companion object {
        const val DUMMY_QUERY_NAME = "UTXO_DUMMY_QUERY"
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            CustomQueryFlowRequest::class.java
        )

        val resultSet = utxoLedgerService.query(DUMMY_QUERY_NAME, String::class.java)
           .setParameter("testField", "dummy")
           .setOffset(request.offset)
           .setLimit(request.limit)
           .execute()

        return jsonMarshallingService.format(
            CustomQueryFlowResponse(resultSet.results)
        )
    }
}
