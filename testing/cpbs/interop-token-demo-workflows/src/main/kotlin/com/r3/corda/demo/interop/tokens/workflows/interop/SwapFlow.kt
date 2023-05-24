package com.r3.corda.demo.interop.tokens.workflows.interop

import com.r3.corda.demo.interop.tokens.contracts.TokenContract
import com.r3.corda.demo.interop.tokens.states.TokenState
import com.r3.corda.demo.interop.tokens.workflows.TransferFlowArgs
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant


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
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("TransferFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferFlowArgs::class.java)

            val stateId = flowArgs.stateId

            val unconsumedStates = ledgerService.findUnconsumedStatesByType(TokenState::class.java)
            val unconsumedStatesWithId = unconsumedStates.filter { it.state.contractState.linearId == stateId }

            if (unconsumedStatesWithId.size != 1) {
                throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")
            }

            val stateAndRef = unconsumedStatesWithId.first()
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

            //val session1 = flowMessaging.initiateFlow(ownerInfo.name)
            val session2 = flowMessaging.initiateFlow(newOwnerInfo.name)

            val finalizationResult = ledgerService.finalize(
                signedTransaction,
                listOf(session2)
            )
            val userResult = finalizationResult.transaction.id.toString().also {
                log.info("Success! Response: $it")
            }

            //session2.send(Payment(toReserve = BigDecimal(100)))

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
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizationResult = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.outputContractStates.first() as TokenState
                log.info("Verified the transaction/state - ${ledgerTransaction.id}/${state.linearId}")
            }
            log.info("Finished responder flow - ${finalizationResult.transaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }

//        val msg = session.receive(Payment::class.java)
//        log.info("Received message: $msg")
//        val myAlias = memberLookup.myInfo().name
//        val facadeId = "org.corda.interop/platform/tokens/v1.0"
//        log.info("Interop call: $facadeId, $myAlias, ${msg.interopGroupId}")
//        val client: TokensFacade =
//            facadeService.getFacade(facadeId, TokensFacade::class.java, myAlias, msg.interopGroupId)
//        val responseObject = client.reserveTokensV1("USD", msg.toReserve)
//        val response = responseObject.result.toString()
//        log.info(response)
    }
}