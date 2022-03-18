package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.SessionEventFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class InitiateFlowRequestHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = SessionEventFactory::class)
    private val sessionEventFactory: SessionEventFactory,
) : FlowRequestHandler<FlowIORequest.InitiateFlow> {

    override val type = FlowIORequest.InitiateFlow::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.InitiateFlow): WaitingFor {
        return WaitingFor(SessionConfirmation(listOf(request.sessionId), SessionConfirmationType.INITIATE))
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.InitiateFlow
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val now = Instant.now()

        val payload = SessionInit.newBuilder()
            // Throw an error if a non initiating flow is trying to create this session
            .setFlowName(checkpoint.flowStack.peekFirst().flowName)
            .setFlowId(checkpoint.flowId)
            .setCpiId(checkpoint.flowStartContext.cpiId)
            // TODO Need member lookup service to get the holding identity of the peer
            .setInitiatedIdentity(HoldingIdentity(request.x500Name.toString(), "flow-worker-dev"))
            .setInitiatingIdentity(checkpoint.holdingIdentity)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
            .build()

        val event = SessionEvent.newBuilder()
            .setSessionId(request.sessionId)
            .setMessageDirection(MessageDirection.OUTBOUND)
            .setTimestamp(now)
            .setSequenceNum(null)
            .setInitiatingIdentity(flowKey.identity)
            // TODO Need member lookup service to get the holding identity of the peer
            .setInitiatedIdentity(HoldingIdentity(request.x500Name.toString(), "flow-worker-dev"))
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
            .setPayload(payload)
            .build()

        val sessionState = sessionManager.processMessageToSend(
            key = checkpoint.flowId,
            sessionState = null,
            event = sessionEventFactory.create(request.sessionId, now, payload),
            instant = now
        )

        checkpoint.putSessionState(sessionState)

        return context
    }
}