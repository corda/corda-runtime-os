package com.r3.corda.demo.utxo

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
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import com.r3.corda.demo.utxo.contract.TestCommand
import com.r3.corda.demo.utxo.contract.TestUtxoState
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Example utxo flow.
 * TODO expand description
 */

@InitiatingFlow(protocol = "utxo-flow-protocol")
class UtxoDemoFlow : ClientStartableFlow {
    /*
     * Make a transaction given a list of X500 name (`members`) and an arbitrary string (`input`), and
     * possibly the X500 name of a notary (`notary`). `members` will typically be the other parties to the transaction,
     * not the party running this flow who is initiating the transaction.
     *
     * For each entry in `members`, initiate a flow session. Use these sessions to initiate a system finality
     * flow by calling `UtxoLedgerService.finalize`, which will wait for responses and either assemble a
     * `SignedTransaction` or fail.
     */
    data class InputMessage(val input: String, val members: List<String>, val notary: String?)

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Utxo flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java)
            val myInfo = memberLookup.myInfo()
            val members = request.members.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }
            val testUtxoState = TestUtxoState(
                request.input,
                members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first(),
                request.members + listOf(myInfo.name.toString())
            )

            val notary = if (request.notary == null) {
                notaryLookup.notaryServices.single()
            } else {
                requireNotNull(notaryLookup.lookup(MemberX500Name.parse(request.notary))) {
                    "Given notary ${request.notary} is invalid"
                }
            }
            val txBuilder = utxoLedgerService.createTransactionBuilder()

            val signedTransaction = txBuilder
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(testUtxoState)
                .addCommand(TestCommand())
                .addSignatories(testUtxoState.participants)
                .toSignedTransaction()

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            return try {
                val finalizationResult = utxoLedgerService.finalize(
                    signedTransaction,
                    sessions
                )
                finalizationResult.transaction.id.toString().also {
                    log.info("Success! Response: $it")
                }

            } catch (e: Exception) {
                log.warn("Finality failed", e)
                "Finality failed, ${e.message}"
            }
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "utxo-flow-protocol")
class UtxoResponderFlow : ResponderFlow {
    /*
     * Wait for and handle a single receive finality record from a UtxoDemoFlow, and respond by either:
     *   - if validation succeeded: send back Payload with a list of DigitalSignatureAndMetadata records,
     *   - otherwise since validation failed: send back a Payload.Failure error with a reason string
     *
     * This is a responder flow, so it gets run by the platform when another flow in the session manager
     * which has the InitiatingFlow set to the same protocol label "utxo-flow-protocol". In our case,
     * UtxoDemoFlow above has the same protocol label, so Corda will handle starting this flow on a different
     * virtual node provide the virtual nodes are in the same session manager.
     *
     * In particular, the `finalize` on the `UtxoDemoFlow` above launches a system flow, which then sends
     * a `FinalityPayload` with the protocol label `utxo-flow-protocol`, so we must make sure we are setup to handle that.
     * We do that by calling UtxoLedgerService.receiveFinality`, which ends up executing
     * `UtxoReceiveFinalityFlowV1.receiveTransactionAndBackchain`,
     * which does a `session.receive(FinalityPayload::class.java)`.
     */
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizationResult = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.outputContractStates.first() as TestUtxoState
                if (state.testField == "fail") {
                    log.info("Failed to verify the transaction - ${ledgerTransaction.id}")
                    throw IllegalStateException("Failed verification")
                }
                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info("Finished responder flow - ${finalizationResult.transaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
