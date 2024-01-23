package com.r3.corda.demo.utxo

import net.corda.v5.application.crypto.DigestService
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
import com.r3.corda.demo.utxo.contract.TestCommand
import com.r3.corda.demo.utxo.contract.TestUtxoState
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@InitiatingFlow(protocol = "utxo-evolve-protocol")
class UtxoDemoEvolveFlow : ClientStartableFlow {

    data class EvolveMessage(val update: String, val transactionId: String, val index: Int, val newParticipant: String?)
    data class EvolveResponse( val transactionId: String?, val errorMessage: String?)

    class EvolveFlowError(message: String): Exception(message)

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var digestService: DigestService

    private val log = LoggerFactory.getLogger(this::class.java)


    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Utxo flow demo starting... v2")
        val response = try {
            val request = requestBody.getRequestBodyAs(jsonMarshallingService, EvolveMessage::class.java)

            val inputTx =
                utxoLedgerService.findLedgerTransaction(digestService.parseSecureHash(request.transactionId))
                    ?: throw EvolveFlowError("Failed to find transaction ${request.transactionId}")

            val prevStates = inputTx.outputStateAndRefs
            if (prevStates.size <= request.index)
                throw EvolveFlowError( "Invalid state index ${request.index} - transaction " +
                        "${request.transactionId} only has ${prevStates.size + 1} outputs.")

            val input = prevStates[request.index]
            val inputState = input.state.contractState as? TestUtxoState ?:
                throw EvolveFlowError( "State ${prevStates[request.index].ref} is not of type TestUtxoState")

            val outParticipants = if (request.newParticipant != null) {
                log.info("adding new participant ${request.newParticipant}")
                val newParticipantInfo = memberLookup.lookup(MemberX500Name.parse(request.newParticipant))
                if (newParticipantInfo == null) {
                    val msg ="new member ${request.newParticipant} not found"
                    log.error(msg)
                    throw IllegalStateException(msg)
                } else {
                    val newKey = newParticipantInfo.ledgerKeys.first()
                    log.info("adding new participant ${request.newParticipant} key $newKey")
                    inputState.participants + newKey
                }
            } else inputState.participants
            val output =
                TestUtxoState(
                    request.update,
                    outParticipants,
                    inputState.participantNames
                )

            val members = output.participantNames.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }

            val signedTransaction = utxoLedgerService.createTransactionBuilder()
                .addCommand(TestCommand())
                .addOutputState(output)
                .addInputState(input.ref)
                .setNotary(input.state.notaryName)
                .setTimeWindowUntil(Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addSignatories(output.participants)
                .toSignedTransaction()

            val sessions = (members - memberLookup.myInfo()).map { flowMessaging.initiateFlow(it.name) }

            val finalizationResult = utxoLedgerService.finalize(
                    signedTransaction,
                    sessions
                )

            val transactionId = finalizationResult.transaction.id.toString()
            EvolveResponse(transactionId, null).also {
                log.info("Success! Response: $it")
            }
        }
        catch (e: Exception){
            EvolveResponse(null,"Flow failed: ${e.message}")
        }

        return jsonMarshallingService.format(response)
    }
}


@InitiatedBy(protocol = "utxo-evolve-protocol")
class UtxoEvolveResponderFlow : ResponderFlow {

    private val log = LoggerFactory.getLogger(this::class.java)

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
