package net.cordapp.testing.packagingverification

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.cordapp.testing.packagingverification.contract.SimpleState

class ReportStatesFlow : ClientStartableFlow {
    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    override fun call(requestBody: ClientRequestBody): String {
        val unconsumedStates = utxoLedgerService.findUnconsumedStatesByType(SimpleState::class.java)
        val result = unconsumedStates.fold(0L) { acc, stateAndRef ->
            acc + stateAndRef.state.contractState.value
        }

        return result.toString()
    }
}
