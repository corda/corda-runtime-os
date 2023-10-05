package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.states.Asset
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory


@InitiatingFlow(protocol = "finalize-payment-protocol")
class FinalizeFlow(
    private val signedTransaction: UtxoSignedTransaction,
    private val otherMember: List<MemberX500Name>
) :
    SubFlow<String> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): String {
        log.info("FinalizeFlow.call() called")

        val sessions = otherMember.map { flowMessaging.initiateFlow(it) }

        return try {
            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                sessions
            )
            finalizedSignedTransaction.transaction.id.toString().also {
                log.info("Success! Response: $it")
            }
        } catch (e: Exception) {
            log.warn("Finality failed", e)
            "Finality failed, ${e.message}"
        }
    }
}


@InitiatedBy(protocol = "finalize-payment-protocol")
class FinalizeResponderFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("FinalizeResponderFlow.call() called")

        try {
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(Asset::class.java).singleOrNull()
                    ?: throw CordaRuntimeException("Failed verification - transaction did not have exactly one output state.")
                log.info("Output state id - ${state.assetId}") // Temporally added to suppress compilation warning
                log.info("Verified the transaction - ${ledgerTransaction.id}")
            }
            log.info("Finished responder flow - ${finalizedSignedTransaction.transaction.id}")
        } catch (e: Exception) {
            log.warn("Responder flow finished with an exception.", e)
        }
    }
}
