package com.r3.corda.notary.plugin.common.repair

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.repair.UtxoLedgerRepairService
import java.time.Duration
import java.time.Instant

class NotarizedTransactionRepairFlow : ClientStartableFlow {

    @CordaInject
    private lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    private lateinit var utxoLedgerRepairService: UtxoLedgerRepairService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val parameters = requestBody.getRequestBodyAs(jsonMarshallingService, Parameters::class.java)
        utxoLedgerRepairService.repairTransactions(
            Instant.ofEpochMilli(parameters.from),
            Instant.ofEpochMilli(parameters.until),
            Duration.ofSeconds(parameters.duration)
        )
        return ""
    }

    data class Parameters(val from: Long, val until: Long, val duration: Long)
}