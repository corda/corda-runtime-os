package net.cordapp.demo.utxo.token

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.utxo.UtxoLedgerService

@InitiatedBy("utxo-coin-protocol")
class CreateCoinResponder : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) {
                log.info("Verified the transaction- ${it.id}")
            }
            log.info("Finished responder flow - ${finalizedSignedTransaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished create coin responder flow", e)
        }
    }
}

