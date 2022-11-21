package net.cordapp.demo.utxo

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
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.security.PublicKey
import java.time.Instant

/**
 * Example utxo flow. Currently, does almost nothing other than verify that
 * we can inject the ledger service. Eventually it should do a two-party IOUState
 * agreement.
 */

@InitiatingFlow("utxo-flow-protocol")
class UtxoDemoFlow : RPCStartableFlow {
    data class InputMessage(val input: String, val members: List<String>)

    class TestUtxoState(
        val testField: String,
        override val participants: List<PublicKey>
    ) : ContractState

    class TestCommand : Command

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Utxo flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)

            val myInfo = memberLookup.myInfo()
            val members = request.members.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }

            val testUtxoState = TestUtxoState(
                request.input,
                members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first()
            )

            val txBuilder = utxoLedgerService.getTransactionBuilder()
            @Suppress("DEPRECATION")
            val signedTransaction = txBuilder
                .setNotary(Party(myInfo.name, myInfo.ledgerKeys.first())) // TODO fix notary
                .setTimeWindowBetween(Instant.MIN, Instant.MAX)
                .addOutputState(testUtxoState)
                .addCommand(TestCommand())
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }
            val finalizedSignedTransaction = utxoLedgerService.finalize(
                signedTransaction,
                sessions
            )

            val resultMessage = finalizedSignedTransaction.id.toString()
            log.info("Success! Response: $resultMessage")
            return resultMessage
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

@InitiatedBy("utxo-flow-protocol")
class UtxoResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
            val state = ledgerTransaction.outputContractStates.first() as UtxoDemoFlow.TestUtxoState
            if (state.testField == "fail") {
                log.info("Failed to verify the transaction - $ledgerTransaction")
                throw IllegalStateException("Failed verification")
            }
            log.info("Verified the transaction- $ledgerTransaction")
        }

        log.info("Finished responder flow - $finalizedSignedTransaction")
    }
}
