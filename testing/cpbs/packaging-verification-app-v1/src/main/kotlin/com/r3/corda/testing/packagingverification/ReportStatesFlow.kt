package com.r3.corda.testing.packagingverification

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.ledger.utxo.UtxoLedgerService
import com.r3.corda.testing.packagingverification.contract.SimpleState
import net.corda.v5.base.annotations.Suspendable

class ReportStatesFlow : ClientStartableFlow {
    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        @Suppress("Deprecation") // Call to be replaced in CORE-17745
        val unconsumedStates = utxoLedgerService.findUnconsumedStatesByType(SimpleState::class.java)
        val result = unconsumedStates.fold(0L) { acc, stateAndRef ->
            acc + stateAndRef.state.contractState.value
        }

        return result.toString()
    }
}
