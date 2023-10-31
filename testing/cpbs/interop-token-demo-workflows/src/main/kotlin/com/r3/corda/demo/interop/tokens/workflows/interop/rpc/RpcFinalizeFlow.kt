package com.r3.corda.demo.interop.tokens.workflows.interop.rpc

import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
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


@InitiatingFlow(protocol = "rpc-finalize-payment-protocol")
class RpcFinalizeFlow(private val signedTransaction: UtxoSignedTransaction, private val otherMember: List<MemberX500Name>):
    SubFlow<DigitalSignatureAndMetadata> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): DigitalSignatureAndMetadata {
        log.info("RpcFinalizeFlow.call() called")

        val sessions = otherMember.map { flowMessaging.initiateFlow(it) }


            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                sessions
            )

            finalizedSignedTransaction.transaction.signatures.forEach {
                it.signature.by
            }

            return finalizedSignedTransaction.transaction.signatures.first { it.proof != null }.also {
                log.info("Success! Response: $it")
            }
    }
}


@InitiatedBy(protocol = "rpc-finalize-payment-protocol")
class RpcFinalizeResponderFlow: ResponderFlow {

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
                val state = ledgerTransaction.getOutputStates(TokenState::class.java).singleOrNull() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output state.")
                log.info("Output state id - ${state.linearId}") // Temporally added to suppress compilation warning
                log.info("Verified the transaction - ${ledgerTransaction.id}")
            }
            log.info("Finished responder flow - ${finalizedSignedTransaction.transaction.id}")
        } catch (e: Exception) {
            log.warn("Responder flow finished with an exception.", e)
        }
    }
}
