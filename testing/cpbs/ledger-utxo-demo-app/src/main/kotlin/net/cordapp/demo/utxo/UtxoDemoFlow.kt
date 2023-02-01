package net.cordapp.demo.utxo

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.days
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.cordapp.demo.utxo.contract.TestCommand
import net.cordapp.demo.utxo.contract.TestUtxoState
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Example utxo flow.
 * TODO expand description
 */

@InitiatingFlow("utxo-flow-protocol")
class UtxoDemoFlow : ClientStartableFlow {
    data class InputMessage(val input: String, val members: List<String>, val notary: String)

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
    override fun call(requestBody: RestRequestBody): String {
        log.info("Utxo flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)
            log.info(">>Utxo flow demo starting...01")

            val myInfo = memberLookup.myInfo()
            log.info(">>Utxo flow demo starting...02")
            val members = request.members.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }
            log.info(">>Utxo flow demo starting...03")
            val testUtxoState = TestUtxoState(
                request.input,
                members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first()
            )
            log.info(">>Utxo flow demo starting...04")

            val notary = notaryLookup.notaryServices.single()
            val txBuilder = utxoLedgerService.getTransactionBuilder()
            log.info(">>Utxo flow demo starting...05")

            val signedTransaction = txBuilder
                .setNotary(Party(notary.name, notary.publicKey))
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.toMillis()))
                .addOutputState(testUtxoState)
                .addCommand(TestCommand())
                .addSignatories(testUtxoState.participants)
                .toSignedTransaction()
            log.info(">>Utxo flow demo starting...06")

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            log.info(">>Utxo flow demo starting...07")

            return try {
                val finalizedSignedTransaction = utxoLedgerService.finalize(
                    signedTransaction,
                    sessions
                )
                log.info(">>Utxo flow demo starting...08")
                finalizedSignedTransaction.id.toString().also {
                    log.info("Utxo flow demo starting...09")
                    log.info("Success! Response: $it")
                }

            } catch (e: Exception) {
                log.info(">>Utxo flow demo starting...10")
                log.warn("Finality failed", e)
                "Finality failed, ${e.message}"
            }
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            log.info(">>Utxo flow demo starting...11")
            throw e
        }
    }
}

@InitiatedBy("utxo-flow-protocol")
class UtxoResponderFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        log.info(">>Utxo flow demo starting...21")
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                log.info(">>Utxo flow demo starting...22")
                val state = ledgerTransaction.outputContractStates.first() as TestUtxoState
                if (state.testField == "fail") {
                    log.info(">>Utxo flow demo starting...23")
                    log.info("Failed to verify the transaction - ${ledgerTransaction.id}")
                    throw IllegalStateException("Failed verification")
                }
                log.info(">>Utxo flow demo starting...24")
                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info(">>Utxo flow demo starting...25")
            log.info("Finished responder flow - ${finalizedSignedTransaction.id}")
        } catch (e: Exception) {
            log.info(">>Utxo flow demo starting...26")
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
