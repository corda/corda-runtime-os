package com.r3.corda.demo.interop.tokens.workflows.interop

import com.r3.corda.demo.interop.tokens.contracts.TokenContract
import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.UUID


data class SwapFlowArgs(val newOwner: String, val stateId: UUID, val applicationName: String,
                        val recipientOnOtherLedger: String)

@InitiatingFlow(protocol = "interop-sample-swap-protocol")
class SwapFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("SwapFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, SwapFlowArgs::class.java)

            val stateId = flowArgs.stateId

            val unconsumedStates = ledgerService.findUnconsumedStatesByType(TokenState::class.java)
            val stateAndRef = unconsumedStates.firstOrNull { it.state.contractState.linearId == stateId }
                ?:  throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")

            val inputState = stateAndRef.state.contractState

            val myInfo = memberLookup.myInfo()
            val ownerInfo = memberLookup.lookup(inputState.owner)
                ?: throw CordaRuntimeException("MemberLookup can't find current state owner.")
            val newOwnerInfo = memberLookup.lookup(MemberX500Name.parse(flowArgs.newOwner))
                ?: throw CordaRuntimeException("MemberLookup can't find new state owner.")

            if (myInfo.name != ownerInfo.name) {
                throw CordaRuntimeException("Only the owner of a state can transfer it to a new owner.")
            }

            val outputState =
                inputState.withNewOwner(newOwnerInfo.name, listOf(ownerInfo.ledgerKeys[0], newOwnerInfo.ledgerKeys[0]))

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(stateAndRef.state.notaryName)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(stateAndRef.ref)
                .addOutputState(outputState)
                .addCommand(TokenContract.Transfer())
                .addSignatories(outputState.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val notaries = notaryLookup.notaryServices
            require(notaries.isNotEmpty()) { "No notaries are available." }
            require(notaries.size == 1) { "Too many notaries $notaries." }
            val notary = notaryLookup.notaryServices.single()
            val byteArrayKey: ByteArray = notary.publicKey.encoded

            val session = flowMessaging.initiateFlow(newOwnerInfo.name)
            val reservation : String = session.sendAndReceive(String::class.java,
                DraftTx(flowArgs.applicationName, flowArgs.recipientOnOtherLedger, signedTransaction.id, byteArrayKey))

            //ledgerService.sendAndReceiveTransactionBuilder() //TODO use this so other party can inspect and reason

            log.info("Reserved $reservation")

            //TODO check my alter ego / pool for confirmation about a receive of locked state
            val finalizationResult = ledgerService.finalize(signedTransaction, listOf(session))

            val userResult = finalizationResult.transaction.id.toString().also {
                log.info("Success! Response: $it")
            }

            val notarySignature: DigitalSignatureAndMetadata = finalizationResult.transaction.signatures.first { it.proof != null }

            val result = flowEngine.subFlow(SwapUnlockSubFlow(flowArgs.applicationName, notarySignature, reservation))
            log.info("SwapFlow - unlocking success! Response: $result")

            return userResult
        } catch (e: Exception) {
            log.warn("Failed to process SwapFlow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "interop-sample-swap-protocol")
class SwapResponderFlow : ResponderFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(session: FlowSession) {

        val msg = session.receive(DraftTx::class.java)
        log.info("Received message: $msg")
        log.info("locking send by ${memberLookup.myInfo().name}")
        val result = flowEngine.subFlow(SwapResponderSubFlow(msg.applicationName, msg.recipientOnOtherLedger,
            ByteBuffer.wrap(msg.notaryKey), msg.draftTxId))
        session.send(result)

        try {
            val finalizationResult = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.outputContractStates.first() as TokenState
                log.info("Verified the transaction/state - ${ledgerTransaction.id}/${state.linearId}")
            }
            log.info("Finished responder flow - ${finalizationResult.transaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }

    }
}