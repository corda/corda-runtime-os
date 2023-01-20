package net.cordapp.demo.consensual

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.cordapp.demo.consensual.contract.TestConsensualState

/**
 * Example consensual flow.
 * TODO expand description
 */

@InitiatingFlow("consensual-flow-protocol")
class ConsensualDemoFlow : ClientStartableFlow {
    data class InputMessage(val input: String, val members: List<String>)

    private companion object {
        val log = contextLogger()
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
    override fun call(requestBody: RestRequestBody): String {
        log.info("Consensual flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)

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

            val txBuilder = consensualLedgerService.getTransactionBuilder()

            @Suppress("DEPRECATION")
            val signedTransaction = txBuilder
                .withStates(testConsensualState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

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

@InitiatedBy("consensual-flow-protocol")
class ConsensualResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
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
