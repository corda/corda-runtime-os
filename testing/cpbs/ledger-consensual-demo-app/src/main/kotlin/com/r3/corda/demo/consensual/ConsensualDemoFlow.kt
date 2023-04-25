package com.r3.corda.demo.consensual

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import com.r3.corda.demo.consensual.contract.TestConsensualState
import org.slf4j.LoggerFactory

/**
 * Example consensual flow.
 * TODO expand description
 */

@InitiatingFlow(protocol = "consensual-flow-protocol")
class ConsensualDemoFlow : ClientStartableFlow {
    data class InputMessage(val input: String, val members: List<String>)

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Consensual flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java)

            val myInfo = memberLookup.myInfo()
            val members = request.members.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }

            val testConsensualState = TestConsensualState(
                request.input,
                members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first()
            )

            val txBuilder = consensualLedgerService.createTransactionBuilder()

            val signedTransaction = txBuilder
                .withStates(testConsensualState)
                .toSignedTransaction()

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            return try {
                val finalizedSignedTransaction = consensualLedgerService.finalize(
                    signedTransaction,
                    sessions
                )
                finalizedSignedTransaction.id.toString().also {
                    log.info("Success! Response: $it")
                }

            } catch (e: Exception) {
                log.warn("Finality failed", e)
                "Finality failed, ${e.message}"
            }
        } catch (e: Exception) {
            log.warn("Failed to process consensual flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "consensual-flow-protocol")
class ConsensualResponderFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = consensualLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.states.first() as TestConsensualState
                if (state.testField == "fail") {
                    log.info("Failed to verify the transaction - $ledgerTransaction")
                    throw IllegalStateException("Failed verification")
                }
                log.info("Verified the transaction- $ledgerTransaction")
            }
            log.info("Finished responder flow - $finalizedSignedTransaction")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
