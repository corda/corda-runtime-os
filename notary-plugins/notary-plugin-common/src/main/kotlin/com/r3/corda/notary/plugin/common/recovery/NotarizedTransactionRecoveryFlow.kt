package com.r3.corda.notary.plugin.common.recovery

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant

class NotarizedTransactionRecoveryFlow : ClientStartableFlow {

    @CordaInject
    private lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val parameters = requestBody.getRequestBodyAs(jsonMarshallingService, Parameters::class.java)
        utxoLedgerService.recoverMissedNotarisedTransactions(Instant.ofEpochMilli(parameters.instant))
        return ""
    }

    data class Parameters(val instant: Long)
}