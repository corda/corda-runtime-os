package net.cordapp.demo.consensual

import java.security.PublicKey
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransactionVerifier

/**
 * Example consensual flow. Currently, does almost nothing other than verify that
 * we can inject the ledger service. Eventually it should do a two-party IOUState
 * agreement.
 */

class TestPartyImpl(override val name: MemberX500Name, override val owningKey: PublicKey) : Party

@InitiatingFlow("consensual-flow-protocol")
class ConsensualFlow : RPCStartableFlow {
    data class InputMessage(val number: Int)
    data class ResultMessage(val text: String)

    class TestConsensualState(
        val testField: String,
        override val participants: List<Party>
    ) : ConsensualState {
        override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    }

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

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
            val member = memberLookup.lookup(MemberX500Name("Bob", "London", "GB"))!!

            val testConsensualState =
                TestConsensualState(
                    "test",
                    listOf(
                        TestPartyImpl(
                            member.name,
                            member.ledgerKeys.first()
                        )
                    )
                )

            val txBuilder = consensualLedgerService.getTransactionBuilder()
            val signedTransaction = txBuilder
                .withStates(testConsensualState)
                .signInitial(memberLookup.myInfo().ledgerKeys.first())

            val finalizedSignedTransaction = consensualLedgerService.finality(
                signedTransaction,
                listOf(flowMessaging.initiateFlow(member.name))
            )

            val resultMessage = ResultMessage(text = finalizedSignedTransaction.toString())
            log.info("Success! Response: $resultMessage")
            return jsonMarshallingService.format(resultMessage)
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

        val finalizedSignedTransaction = consensualLedgerService.receiveFinality(
            session,
            // Cannot have a normal lambda without providing an `inline` version of this method.
            object : ConsensualSignedTransactionVerifier {
                override fun verify(signedTransaction: ConsensualSignedTransaction) {
                    log.info("Verified the transaction with a callback 12345 - $signedTransaction")
                }
            }
        )
        log.info("Finished responder flow - $finalizedSignedTransaction")
    }
}