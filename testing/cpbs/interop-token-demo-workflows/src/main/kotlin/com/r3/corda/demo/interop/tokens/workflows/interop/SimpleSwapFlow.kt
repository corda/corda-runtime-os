package com.r3.corda.demo.interop.tokens.workflows.interop

import com.r3.corda.demo.interop.tokens.contracts.TokenContract
import com.r3.corda.demo.interop.tokens.states.TokenState
import com.r3.corda.demo.interop.tokens.workflows.IssueFlowResult
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import net.corda.v5.application.interop.facade.FacadeId


@InitiatingFlow(protocol = "interop-sample-simple-swap-protocol")
class SimpleSwapFlow : ClientStartableFlow {

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
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var interopIdentityLookUp : InteropIdentityLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("TransferFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, SwapFlowArgs::class.java)

            val stateId = flowArgs.stateId

            @Suppress("deprecation")
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

            val session1 = flowMessaging.initiateFlow(newOwnerInfo.name)

            val finalizationResult = ledgerService.finalize(
                signedTransaction,
                listOf(session1)
            )

            val userResult = finalizationResult.transaction.id.toString().also {
                log.info("Success! Response: $it")
            }

            val payment = Payment(flowArgs.applicationName, BigDecimal(100))
            val myInteropIdentityInfo = checkNotNull(interopIdentityLookUp.lookup(flowArgs.applicationName)) {
                "Cant find InteropInfo for ${flowArgs.applicationName}."
            }
            val myInteropIdentityName = MemberX500Name.parse(myInteropIdentityInfo.x500Name)

            val facadeId = FacadeId("org.corda.interop", listOf("platform", "tokens"), "v3.0")
            log.info("Interop call: $facadeId, $myInteropIdentityName")
            val tokens: TokensFacade =
                facadeService.getProxy(facadeId, TokensFacade::class.java, myInteropIdentityInfo)

            val response= tokens.reserveTokensV3("USD", payment.toReserve, 1000L)
            log.info("Interop call returned: $response")

            return jsonMarshallingService.format(IssueFlowResult(userResult, outputState.linearId.toString()))
        } catch (e: Exception) {
            log.warn("Failed to process SwapFlow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "interop-sample-simple-swap-protocol")
class SimpleSwapResponderFlow : ResponderFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

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

    }
}