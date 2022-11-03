package net.cordapp.demo.consensual

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
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
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey

/**
 * Example consensual flow. Currently, does almost nothing other than verify that
 * we can inject the ledger service. Eventually it should do a two-party IOUState
 * agreement.
 */

@InitiatingFlow("consensual-flow-protocol")
class ConsensualDemoFlow : RPCStartableFlow {
    data class InputMessage(val input: String, val members: List<String>)

    class TestConsensualState(
        val testField: String,
        override val participants: List<PublicKey>
    ) : ConsensualState {
        override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    }

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
    override fun call(requestBody: RPCRequestData): String {
        log.info("Consensual flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)

            val alice = memberLookup.myInfo()
            val members = request.members.map { memberLookup.lookup(MemberX500Name.parse(it))!! }

            val testConsensualState = TestConsensualState(
                request.input,
                members.map { it.ledgerKeys.first() } + alice.ledgerKeys.first()
            )

            val txBuilder = consensualLedgerService.getTransactionBuilder()
            val signedTransaction = txBuilder
                .withStates(testConsensualState)
                .sign(alice.ledgerKeys.first())

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }
            val finalizedSignedTransaction = consensualLedgerService.finality(
                signedTransaction,
                sessions
            )

            val resultMessage = finalizedSignedTransaction.id.toString()
            log.info("Success! Response: $resultMessage")
            return resultMessage
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
        val finalizedSignedTransaction = consensualLedgerService.receiveFinality(session) { signedTransaction ->
            val ledgerTransaction = signedTransaction.toLedgerTransaction()
            val state = ledgerTransaction.states.first() as ConsensualDemoFlow.TestConsensualState
            if (state.testField == "fail") {
                log.info("Failed to verify the transaction - $signedTransaction")
                throw IllegalStateException("Failed verification")
            }
            log.info("Verified the transaction- $signedTransaction")
        }

        log.info("Finished responder flow - $finalizedSignedTransaction")
    }
}
