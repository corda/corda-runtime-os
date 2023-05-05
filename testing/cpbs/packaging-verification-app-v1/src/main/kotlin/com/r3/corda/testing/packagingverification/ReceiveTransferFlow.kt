package com.r3.corda.testing.packagingverification

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory

@InitiatedBy(protocol = "com.r3.corda.testing.packagingverification.TransferStatesFlow")
class ReceiveTransferFlow : ResponderFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("Receiving states")

        utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
            log.info("Validating finality with ${ledgerTransaction.outputContractStates.size} output states")

            require(ledgerTransaction.outputContractStates.size <= 2)
                { "Too many output states for this kind of transaction" }
            require(ledgerTransaction.outputContractStates.size > 0)
                { "Too few output states for this kind of transaction" }
        }
        log.info("Finality received and validated")
    }
}
