package net.cordapp.testing.packagingverification

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory

@InitiatedBy(protocol = "net.cordapp.testing.packagingverification.TransferStatesFlow")
class ReceiveTransferFlow : ResponderFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    override fun call(session: FlowSession) {
        log.info("Receiving states")

        utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
            log.info("Validating finality with ${ledgerTransaction.outputContractStates.size} output states")

            if (ledgerTransaction.outputContractStates.size > 2) {
                throw CordaRuntimeException("Too many output states for this kind of transaction")
            }
            if (ledgerTransaction.outputContractStates.size == 0) {
                throw CordaRuntimeException("Too few output states for this kind of transaction")
            }
        }
        log.info("Finality received and validated")
    }
}
