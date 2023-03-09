package net.corda.flow.pipeline.handlers.requests.sessions.service

import java.time.Instant
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.keyValuePairListOf
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSIONS_SUPPORTED
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [InitiateFlowRequestService::class])
class InitiateFlowRequestService @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun getSessionsNotInitiated(
        context: FlowEventContext<Any>,
        sessionToInfo: Set<FlowIORequest.SessionInfo>
    ): Set<FlowIORequest.SessionInfo> {
        val checkpoint = context.checkpoint
        return sessionToInfo.filter { checkpoint.getSessionState(it.sessionId) == null }.toSet()
    }

    fun initiateFlowsNotInitiated(
        context: FlowEventContext<Any>,
        sessionToInfo: Set<FlowIORequest.SessionInfo>,
    ) {
        val sessionsNotInitiated = getSessionsNotInitiated(context, sessionToInfo)
        if (sessionsNotInitiated.isNotEmpty()) {
            initiateFlows(context, sessionsNotInitiated)
        }
    }

    @Suppress("ThrowsCount")
    private fun initiateFlows(
        context: FlowEventContext<Any>,
        sessionsNotInitiated: Set<FlowIORequest.SessionInfo>
    ) {
        val checkpoint = context.checkpoint

        logger.trace { "Initiating flows with sessionIds ${sessionsNotInitiated.map { it.sessionId }}" }
        // throw an error if the session already exists (shouldn't really get here for real, but for this class, it's not valid)
        val protocolStore = try {
            flowSandboxService.get(checkpoint.holdingIdentity, checkpoint.cpkFileHashes).protocolStore
        } catch (e: Exception) {
            throw FlowTransientException(
                "Failed to get the flow sandbox for identity ${checkpoint.holdingIdentity}: " +
                        (e.message?: "No exception message provided."), e
            )
        }

        val flowStack = checkpoint.flowStack
        if (flowStack.isEmpty()) {
            throw FlowFatalException("Flow stack is empty while trying to initiate a flow")
        }

        val initiatingFlowStackItem = flowStack.nearestFirst { it.isInitiatingFlow }
        val initiator = initiatingFlowStackItem?.flowName
            ?: throw FlowPlatformException("Flow stack did not contain an initiating flow in the stack")

        val (protocolName, protocolVersions) = protocolStore.protocolsForInitiator(initiator, context)

        val sessionContext = KeyValueStore().apply {
            put(FLOW_PROTOCOL, protocolName)
            put(FLOW_PROTOCOL_VERSIONS_SUPPORTED, protocolVersions.joinToString())
        }

        checkpoint.putSessionStates(
            sessionsNotInitiated.map {
                flowSessionManager.sendInitMessage(
                    checkpoint,
                    it.sessionId,
                    it.counterparty,
                    contextUserProperties = keyValuePairListOf(it.contextUserProperties),
                    contextPlatformProperties = keyValuePairListOf(it.contextPlatformProperties),
                    sessionProperties = sessionContext.avro,
                    Instant.now()
                )
            }
        )

        val sessionsNotInitiatedIds = sessionsNotInitiated.map { it.sessionId }
        initiatingFlowStackItem.sessions
            .filter { it.sessionId in sessionsNotInitiatedIds }
            .map { it.initiated = true }
    }
}
