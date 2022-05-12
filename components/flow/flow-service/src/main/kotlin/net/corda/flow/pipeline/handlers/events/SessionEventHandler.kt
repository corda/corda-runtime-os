package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.waiting.sessions.WaitingForSessionInit
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.impl.FlowProtocol
import net.corda.flow.pipeline.sandbox.impl.FlowProtocolStore
import net.corda.session.manager.SessionManager
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowEventHandler::class])
class SessionEventHandler @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowEventHandler<SessionEvent> {

    private companion object {
        val log = contextLogger()
    }

    override val type = SessionEvent::class.java

    override fun preProcess(context: FlowEventContext<SessionEvent>): FlowEventContext<SessionEvent> {
        val checkpoint = context.checkpoint
        val sessionEvent = context.inputEventPayload

        log.info("Session event in handler: ${sessionEvent.payload}")

        val now = Instant.now()

        val updatedSessionState = sessionManager.processMessageReceived(
            sessionEvent.sessionId,
            if (checkpoint.doesExist) checkpoint.getSessionState(sessionEvent.sessionId) else null,
            sessionEvent,
            now
        )

        // Null is returned if duplicate [SessionInit]s are received
        val nextSessionEvent = sessionManager.getNextReceivedEvent(updatedSessionState)
        when (val sessionInit = nextSessionEvent?.payload) {
            is SessionInit -> {
                createInitiatedFlowCheckpoint(context, sessionInit,nextSessionEvent)
            }
        }

        checkpoint.putSessionState(updatedSessionState)

        return context
    }

    private fun createInitiatedFlowCheckpoint(
        context: FlowEventContext<*>,
        sessionInit: SessionInit,
        sessionEvent: SessionEvent
    ) {
        val sessionId = sessionEvent.sessionId
        val initiatingIdentity = sessionEvent.initiatingIdentity
        val initiatedIdentity = sessionEvent.initiatedIdentity
        val protocols = sessionInit.versions.map { FlowProtocol(sessionInit.protocol, it) }
        val protocolStore = flowSandboxService.get(initiatedIdentity.toCorda()).protocolStore
        val initiatedFlow = protocolStore.responderForProtocol(protocols)
        val startContext = FlowStartContext.newBuilder()
            .setStatusKey(FlowKey(sessionId, initiatedIdentity))
            .setInitiatorType(FlowInitiatorType.P2P)
            .setRequestId(sessionId)
            .setIdentity(initiatedIdentity)
            .setCpiId(sessionInit.cpiId)
            .setInitiatedBy(initiatingIdentity)
            .setFlowClassName(initiatedFlow)
            .setCreatedTimestamp(Instant.now())
            .build()

        context.checkpoint.initFromNew(sessionInit.flowId, startContext, WaitingFor(WaitingForSessionInit(sessionId)))
    }

    private fun getInitiatingToInitiatedFlowsFromSandbox(initiatedIdentity: HoldingIdentity): FlowProtocolStore {
        return flowSandboxService.get(initiatedIdentity).protocolStore
    }
}