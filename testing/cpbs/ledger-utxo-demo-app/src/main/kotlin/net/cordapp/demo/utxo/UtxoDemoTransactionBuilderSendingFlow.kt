package net.cordapp.demo.utxo

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
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.cordapp.demo.utxo.contract.TestCommand
import net.cordapp.demo.utxo.contract.TestUtxoState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Example utxo send/receive transaction builder demo
 */

@InitiatingFlow(protocol = "utxo-transaction-builder-sending-protocol")
class UtxoDemoTransactionBuilderSendingFlow : ClientStartableFlow {
    data class InputMessage(val member: String)

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Utxo send transaction builder flow starting...")
        try {
            val request = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java)

            val member = requireNotNull(memberLookup.lookup(MemberX500Name.parse(request.member))) {
                    "Member ${request.member} does not exist in the membership group"
                }

            // The initator just creates a placeholder
            val txBuilder = utxoLedgerService.getTransactionBuilder()

            val session = flowMessaging.initiateFlow(member.name)

            log.info("Call sendAndReceiveTransactionBuilder.")
            val newTxBuilder = utxoLedgerService.sendAndReceiveTransactionBuilder(txBuilder, session)
            // Then it works with the components sent by the other party.

            log.info("Sign received TxBuilder.")
            val signedTransaction = newTxBuilder
                .toSignedTransaction()

            return try {
                log.info("Start finalize.")
                val finalizedSignedTransaction = utxoLedgerService.finalize(
                    signedTransaction,
                    listOf(session)
                )
                finalizedSignedTransaction.id.toString().also {
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

@InitiatedBy(protocol = "utxo-transaction-builder-sending-protocol")
class UtxoTransactionBuilderSendingResponderFlow : ResponderFlow {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            log.info("Call receiveAndReturnTransactionBuilder.")
            val transactionBuilder = utxoLedgerService.receiveTransactionBuilder(session)
            val myInfo = memberLookup.myInfo()
            val counterParty =
                requireNotNull(memberLookup.lookup(session.counterparty)) {
                    "Member ${session.counterparty} does not exist in the membership group"
                }
            val testUtxoState = TestUtxoState(
                "Populated by responder",
                listOf(counterParty.ledgerKeys.first(), myInfo.ledgerKeys.first()),
                listOf(counterParty.name.toString(), myInfo.name.toString())
            )
            val previousUnconsumedState = utxoLedgerService.findUnconsumedStatesByType(TestUtxoState::class.java)
                .first { it.state.contractState.testField == "txbuilder test" }
            val notary = previousUnconsumedState.state.notary
            log.info("Adjust transaction builder.")
            // Responder site amends the transaction builder.
            // In this example everything gets populated here.
            transactionBuilder
                .setNotary(notary)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(previousUnconsumedState.ref)
                .addOutputState(testUtxoState)
                .addCommand(TestCommand())
                .addSignatories(testUtxoState.participants)
            utxoLedgerService.sendUpdatedTransactionBuilder(transactionBuilder, session)
            log.info("receiveFinality")
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.outputContractStates.first() as TestUtxoState
                if (state.testField == "fail") {
                    log.info("Failed to verify the transaction - ${ledgerTransaction.id}")
                    throw IllegalStateException("Failed verification")
                }
                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info("Finished responder flow - ${finalizedSignedTransaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
