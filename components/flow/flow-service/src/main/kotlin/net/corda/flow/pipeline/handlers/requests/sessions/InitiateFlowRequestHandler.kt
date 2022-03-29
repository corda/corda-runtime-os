package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.addOrReplaceSession
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.requireCheckpoint
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class InitiateFlowRequestHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowRequestHandler<FlowIORequest.InitiateFlow> {

    override val type = FlowIORequest.InitiateFlow::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.InitiateFlow): WaitingFor {
        return WaitingFor(SessionConfirmation(listOf(request.sessionId), SessionConfirmationType.INITIATE))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.InitiateFlow): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        val now = Instant.now()

        // throw an error if the session already exists (shouldn't really get here for real, but for this class, it's not valid)

        val flowKey = checkpoint.flowKey
        val payload = SessionInit.newBuilder()
            // Throw an error if a non initiating flow is trying to create this session
            .setFlowName(checkpoint.flowStackItems.first().flowName)
            .setFlowKey(flowKey)
            .setCpiId(checkpoint.flowStartContext.cpiId)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
            .build()

        val event = SessionEvent.newBuilder()
            .setSessionId(request.sessionId)
            .setMessageDirection(MessageDirection.OUTBOUND)
            .setTimestamp(now)
            .setSequenceNum(null)
            .setInitiatingIdentity(flowKey.identity)
            // TODO Need member lookup service to get the holding identity of the peer
            .setInitiatedIdentity(HoldingIdentity(request.x500Name.toString(), "helloworld"))
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
            .setPayload(payload)
            .build()

        val sessionState = sessionManager.processMessageToSend(
            key = checkpoint.flowKey.flowId,
            sessionState = null,
            event = event,
            instant = now
        )

        checkpoint.addOrReplaceSession(sessionState)

        return context
    }
}