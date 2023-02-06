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
import net.corda.v5.base.util.days
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant

@InitiatingFlow("utxo-evolve-protocol")
class UtxoDemoEvolveFlow : RPCStartableFlow {
    data class EvolveMessage(val update: String, val transactionId: String, val index: Int)
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

    private val log = loggerFor<UtxoDemoEvolveFlow>()

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Utxo flow demo starting...")
        val response = try {
            val request = requestBody.getRequestBodyAs<EvolveMessage>(jsonMarshallingService)

            val inputTx = utxoLedgerService.findLedgerTransaction(SecureHash.parse(request.transactionId)) ?:
                throw EvolveFlowError( "Failed to find transaction ${request.transactionId}")

            val prevStates = inputTx.outputStateAndRefs
            if (prevStates.size <= request.index)
                throw EvolveFlowError( "Invalid state index ${request.index} - transaction " +
                        "${request.transactionId} only has ${prevStates.size + 1} outputs.")

            val input = prevStates[request.index]
            val inputState = input.state.contractState as? UtxoDemoFlow.TestUtxoState ?:
                throw EvolveFlowError( "State ${prevStates[request.index].ref} is not of type TestUtxoState")

            val output =
                UtxoDemoFlow.TestUtxoState(
                    request.update,
                    inputState.participants,
                    inputState.participantNames)


            val myInfo = memberLookup.myInfo()
            val members = output.participantNames.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }

            @Suppress("DEPRECATION")
            val signedTransaction = utxoLedgerService.getTransactionBuilder()
                .addCommand(UtxoDemoFlow.TestCommand())
                .addOutputState(output)
                .addInputState(input.ref)
                .setNotary(input.state.notary)
                .setTimeWindowUntil(Instant.now().plusMillis(1.days.toMillis()))
                .addSignatories(output.participants)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            val finalizedSignedTransaction = utxoLedgerService.finalize(
                    signedTransaction,
                    sessions
                )

            val transactionId = finalizedSignedTransaction.id.toString()
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

@InitiatedBy("utxo-evolve-protocol")
class UtxoEvolveResponderFlow : ResponderFlow {

    private val log = LoggerFactory.getLogger(this::class.java)

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.outputContractStates.first() as UtxoDemoFlow.TestUtxoState
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
