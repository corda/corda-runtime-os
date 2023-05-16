package com.r3.corda.demo.interop.delivery.workflows.interop

import com.r3.corda.demo.interop.delivery.states.DeliveryState
import com.r3.corda.demo.interop.delivery.workflows.TransferFlowArgs
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
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
import java.math.BigDecimal


@InitiatingFlow(protocol = "proxy-by-responder-flow-transfer-protocol")
class ProxyByResponderFlow: ClientStartableFlow {

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

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("TransferFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferFlowArgs::class.java)

            val stateId = flowArgs.stateId

            val unconsumedStates = ledgerService.findUnconsumedStatesByType(DeliveryState::class.java)
            val unconsumedStatesWithId = unconsumedStates.filter { it.state.contractState.linearId == stateId }

            if (unconsumedStatesWithId.size != 1) {
                throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")
            }

            val stateAndRef = unconsumedStatesWithId.first()
            val inputState = stateAndRef.state.contractState

            val myInfo = memberLookup.myInfo()
            val ownerInfo = memberLookup.lookup(inputState.owner) ?:
                throw CordaRuntimeException("MemberLookup can't find current state owner.")
            val newOwnerInfo = memberLookup.lookup(MemberX500Name.parse(flowArgs.newOwner)) ?:
                throw CordaRuntimeException("MemberLookup can't find new state owner.")

            if (myInfo.name != ownerInfo.name) {
                throw CordaRuntimeException("Only the owner of a state can transfer it to a new owner.")
            }

            val session = flowMessaging.initiateFlow(newOwnerInfo.name)
            session.send(Payment(toReserve = BigDecimal(100)))

            return "SUCCESS"
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "proxy-by-responder-flow-transfer-protocol")
class ProxyByResponderFlowResponder : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var facadeService: FacadeService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val msg = session.receive(Payment::class.java)
            log.info("Received message: $msg")

            val myAlias = MemberX500Name.parse("C=GB, L=London, O=Bob Alias")
            val facadeId = "org.corda.interop/platform/tokens/v2.0"
            log.info("Interop call: $facadeId, $myAlias, ${msg.interopGroupId}")
            val client : TokensFacade = facadeService.getFacade(facadeId, TokensFacade::class.java, myAlias, msg.interopGroupId)
            val responseObject = client.reserveTokensV2("USD", msg.toReserve, 1000)
            val response = responseObject.result.toString()
            log.info(response)

        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
